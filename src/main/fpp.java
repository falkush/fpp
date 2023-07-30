package main;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.security.CodeSource;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

public class fpp extends JPanel implements KeyListener, FocusListener {
    static final String WindowTile = "Falkush's Puzzle Pack";

    static final Color BacktrackTileColor = new Color(248, 70, 91);
    static final Color BacktrackBorderColor = new Color(174, 29, 12);
    static final Color CurrentTileColor = new Color(225, 203, 90);
    static final Color CurrentTileBorderColor = new Color(188, 129, 90);
    static final Color DefaultTileColor = new Color(58, 174, 235);
    static final Color DefaultTileBorderColor = new Color(186, 119, 3);

    static final Color CharacterColor = new Color(53, 185, 122);
    static final Color ValidatedColor = Color.GREEN;
    static final Color LineColor = Color.BLACK;

    static final int CharacterSize = 20;
    static final int TileSize = 50;
    static final int LineSize = 2;

    static final int LevelsPerRow = 6;

    static final int NumWorlds = 5;
    static final int MinLevelSize = 4;

    static final int Scale = 2;

    static final String SaveFileName = "falkpuzz-savefile.txt";
    static final String LevelsFileName = "levels.dat";

    enum ScreenState {
        MainScreen,
        WorldScreen,
        LevelScreen
    }

    enum Direction {
        Up,
        Down,
        Left,
        Right
    }

    enum UserKey {
        LeaveLevel,
        LeaveWorld,
        Left,
        Right,
        Up,
        Down,
        Reset,
        Backtrack,
        None
    }

    private static final long serialVersionUID = -1808422750914444039L;
    static public boolean focus;

    public static Point Left = new Point(-1, 0);
    public static Point Right = new Point(1, 0);
    public static Point Up = new Point(0, -1);
    public static Point Down = new Point(0, 1);

    static final public JFrame frame = new JFrame(WindowTile);
    static final public JLabel label = new JLabel();

    static public ScreenState currentScreen = ScreenState.MainScreen;
    static public int gamepuzzle = 0;
    static public boolean[] psolve = new boolean[35];
    static public boolean[] psolve2 = new boolean[5];
    static public boolean psolvec;

    static private Direction prevdir = Direction.Down;
    static private Direction[] reg;
    static private int regpos = 1;
    static private boolean[][] board;
    static private boolean[][] puzzle;

    static private Point pos = new Point();

    static BufferedImage mainMenuImage, smileyImage, gameImage;
    static Graphics mainMenuGraphics, gameGraphics;

    static WorldData[] worlds = new WorldData[NumWorlds];
    static int currentWorldIndex = 0;
    static int currentLevelIndex = 0;

    public static void main(String[] args) throws InterruptedException, URISyntaxException {
        LoadWorlds();
        LoadImages();
        mainMenuGraphics = mainMenuImage.getGraphics();

        InitPanel();

        try {
            loadSaveGame(GetSaveFile());
            UpdateSolvedGraphics();
        } catch (IOException e) {
            //
        }
        goToMainMenu();
    }

    static void InitPanel()
    {
        frame.setResizable(false);
        frame.getContentPane().add(new fpp());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(Color.BLACK);

        frame.getContentPane().add(label, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        frame.getContentPane().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                switch (currentScreen) {
                    case MainScreen:
                        HandleMainScreenClick(e.getPoint());
                        return;
                    case WorldScreen:
                        HandleWorldScreenClick(e.getPoint());
                        return;
                    default:
                        return;
                }
            }
        });

        focus = true;
    }

    static void InitLevel(LevelData level) {
        int size = level.size;

        board = new boolean[size][size];
        puzzle = new boolean[size][size];
        reg = new Direction[size * size];

        pos = level.startPos;

        if (pos.y == 0) {
            reg[0] = Direction.Down;
        } else if (pos.y == size - 1) {
            reg[0] = Direction.Up;
        } else if (pos.x == 0) {
            reg[0] = Direction.Right;
        } else {
            reg[0] = Direction.Left;
        }

        prevdir = ReverseDir(reg[0]);

        regpos = 1;

        for (int i = 0; i < level.obstacles.length; i++) {
            puzzle[level.obstacles[i].x][level.obstacles[i].y] = true;
            board[level.obstacles[i].x][level.obstacles[i].y] = true;
        }

        board[pos.x][pos.y] = true;
    }

    static void InitLevelImage(LevelData level) {
        int pixelSize = level.size * TileSize;
        gameImage = new BufferedImage(pixelSize, pixelSize, BufferedImage.TYPE_3BYTE_BGR);
        gameGraphics = gameImage.createGraphics();

        gameGraphics.setColor(DefaultTileColor);
        gameGraphics.fillRect(0, 0, pixelSize, pixelSize); // (04if=-8)
        for (int i = 0; i < level.size; i++) {
            for (int j = 0; j < level.size; j++) {
                if (puzzle[i][j])
                {
                    gameGraphics.setColor(Color.BLACK);
                    gameGraphics.fillRect(TileSize * i, TileSize * j, TileSize, TileSize);
                } else if (i == pos.x && j == pos.y)
                {
                    gameGraphics.setColor(CurrentTileColor);
                    gameGraphics.fillRect(TileSize * i, TileSize * j, TileSize, TileSize); 
                    gameGraphics.setColor(CurrentTileBorderColor);
                    gameGraphics.drawRect(TileSize * i, TileSize * j, TileSize, TileSize);
                    DrawCharacterTile(pos, prevdir);
                }
                else
                {
                    gameGraphics.setColor(DefaultTileBorderColor);
                    gameGraphics.drawRect(TileSize * i, TileSize * j, TileSize, TileSize);
                }
            }
        }
    }

    static LevelData getCurrentLevel() {
        return worlds[currentWorldIndex].levels[currentLevelIndex];
    }

    static void goToMainMenu() {
        currentScreen = ScreenState.MainScreen;
        label.setIcon(new ImageIcon(mainMenuImage));
        frame.pack();
    }

    static void goToWorld(int worldIndex) {
        currentWorldIndex = worldIndex;
        currentScreen = ScreenState.WorldScreen;
        label.setIcon(new ImageIcon(worlds[worldIndex].menuImage));
        frame.pack();
    }

    static void goToLevel(int levelIndex) {
        currentLevelIndex = levelIndex;
        currentScreen = ScreenState.LevelScreen;
        LevelData level = getCurrentLevel();
        InitLevel(level);

        InitLevelImage(level);
        RefreshGameGraphics();
        frame.pack();
    }

    static void HandleWorldScreenClick(Point mouse) {
        if (!focus)
            return;

        if (mouse.y < 45) {
            goToMainMenu();
            return;
        }

        WorldData world = worlds[currentWorldIndex];
        int levelIndex = (mouse.x - 10) / 41 + (world.levels.length > 6 && mouse.y > 119 ? 6 : 0);
        if (levelIndex >= 0 && levelIndex < world.levels.length)
            goToLevel(levelIndex);
    }

    static void HandleMainScreenClick(Point mouse) {
        if (focus && mouse.y > 100)
            goToWorld(mouse.x / 133);
    }

    static void HandleBacktrack() {
        if (currentScreen != ScreenState.LevelScreen || regpos <= 1)
            return;

        regpos--;

        DrawTile(pos, DefaultTileColor, DefaultTileBorderColor);
        board[pos.x][pos.y] = false;
        Direction revertDir = ReverseDir(reg[regpos]);
        Point previousDirVec = DirectionToVec(revertDir);
        pos.x += previousDirVec.x;
        pos.y += previousDirVec.y;


        DrawCharacterTile(pos, revertDir);
        RefreshGameGraphics();
    }

    private static void LoadImages() {
        try {
            mainMenuImage = LoadScaledImageFromResources("mainMenu.png");
            smileyImage = LoadScaledImageFromResources("smiley.png");
            for (int i = 0; i < NumWorlds; i++) {
                worlds[i].menuImage = LoadScaledImageFromResources("world" + i + ".png");
                worlds[i].menuGraphics = worlds[i].menuImage.createGraphics();
            }
        } catch (IOException e1) {
            System.out.println("Unable to load resource files");
            e1.printStackTrace();
            return;
        }
    }

    private static LevelData[] LoadLevels() {
        LevelData[] newLevels = null;
        try (InputStream input = fpp.class.getResourceAsStream("/resources/" + LevelsFileName)) {
            ObjectInputStream objectInput = new ObjectInputStream(input);
            newLevels = (LevelData[]) objectInput.readObject();
            System.out.println("new Levels");
            objectInput.close();
        } catch (ClassNotFoundException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return newLevels;
    }

    private static void LoadWorlds() {
        LevelData[] newLevels = LoadLevels();
        System.out.println("Levels loaded: " + newLevels.length);

        int[] levelsPerWorld = new int[NumWorlds];
        for (LevelData levelData : newLevels) {
            levelsPerWorld[levelData.size - MinLevelSize]++;
        }

        for (int i = 0; i < NumWorlds; i++) {
            worlds[i] = new WorldData();
            worlds[i].levels = new LevelData[levelsPerWorld[i]];
        }

        for (LevelData levelData : newLevels) {
            int worldIndex = levelData.size - MinLevelSize;
            int levelIndex = worlds[worldIndex].levels.length - levelsPerWorld[worldIndex];
            worlds[worldIndex].levels[levelIndex] = levelData;
            levelsPerWorld[worldIndex]--;
        }
    }

    // private static void SaveLevels() {
    // LevelData[] levels = new LevelData[35];
    // for (int i = 0; i < 35; i++) {
    // LevelData level = new LevelData();
    // level.numObstacles = nbo[i];
    // level.size = psize[i];
    // level.startPos = new Point(stx[i], sty[i]);
    // level.obstacles = new Point[nbo[i]];
    // for (int j = 0; j < nbo[i]; j++) {
    // level.obstacles[j] = new Point(puzzx[i][j], puzzy[i][j]);
    // }
    // levels[i] = level;
    // }

    // try (FileOutputStream fos = new FileOutputStream("test.dat")) {
    // ObjectOutputStream oos = new ObjectOutputStream(fos);
    // oos.writeObject(levels);
    // } catch (IOException e) {
    // // TODO Auto-generated catch block
    // e.printStackTrace();
    // }
    // }

    private static BufferedImage LoadScaledImageFromResources(String imageName) throws IOException {
        BufferedImage originalImage = ImageIO.read(fpp.class.getResourceAsStream("/resources/" + imageName));
        Point newSize = new Point(originalImage.getWidth() * Scale, originalImage.getHeight() * Scale);
        BufferedImage result = new BufferedImage(newSize.x, newSize.y, originalImage.getType());
        Graphics image1Graphics = result.getGraphics();
        image1Graphics.drawImage(originalImage, 0, 0, newSize.x, newSize.y, null);
        return result;
    }

    private static void UpdateSolvedGraphics() {
        mainMenuGraphics.setColor(ValidatedColor);
        for (int i = 0; i < NumWorlds; i++) {
            if (psolve2[i])
                mainMenuGraphics.fillRect(60 + 128 * i, 232, 15, 17);
        }
        if (psolvec) {
            mainMenuGraphics.drawImage(smileyImage, 226, 6, 20, 20, null);
            mainMenuGraphics.drawImage(smileyImage, 396, 6, 20, 20, null);
        }
        for (WorldData worldData : worlds) {
            Graphics menu = worldData.menuGraphics;
            menu.setColor(ValidatedColor);
            for (int i = 0; i < worldData.levels.length; i++) {
                LevelData levelData = worldData.levels[i];
                Point rectPos = new Point(
                        29 + 41 * (i % LevelsPerRow),
                        98 + 76 * (i / LevelsPerRow));
                if (levelData.isSolved)
                    menu.fillRect(rectPos.x, rectPos.y, 5, 5);
            }
        }
    }

    public static File GetSaveFile() throws URISyntaxException {
        CodeSource codeSource = fpp.class.getProtectionDomain().getCodeSource(); // Benny Code stackoverflow
        File jarFile = new File(codeSource.getLocation().toURI().getPath());
        File jarDir = jarFile.getParentFile();

        return new File(jarDir, SaveFileName);
    }

    private static void DrawTile(Point pos, Color backgroundColor, Color borderColor) {
        Point startPixel = new Point(TileSize * pos.x, TileSize * pos.y);
        Point size = new Point(TileSize, TileSize);
        gameGraphics.setColor(backgroundColor);
        gameGraphics.fillRect(startPixel.x, startPixel.y, size.x, size.y);
        gameGraphics.setColor(borderColor);
        gameGraphics.drawRect(startPixel.x, startPixel.y, size.x, size.y);
    }

    private static void DrawDirectionTracker(Point pos, Direction direction) {
        Point topLeftPixel = new Point(pos.x * 50, pos.y * 50);
        int halfTile = TileSize / 2;
        int halfLineSize = LineSize / 2;
        Point horizontalSize = new Point(halfTile, LineSize);
        Point verticalSize = new Point(LineSize, halfTile);

        gameGraphics.setColor(LineColor);
        switch (direction) {
            case Left:
                gameGraphics.fillRect(topLeftPixel.x, topLeftPixel.y + halfTile - halfLineSize, horizontalSize.x, horizontalSize.y);
                break;
            case Right:
            gameGraphics.fillRect(topLeftPixel.x + halfTile, topLeftPixel.y + halfTile - halfLineSize, horizontalSize.x, horizontalSize.y);
                break;
            case Up:
            gameGraphics.fillRect(topLeftPixel.x + halfTile - halfLineSize, topLeftPixel.y, verticalSize.x, verticalSize.y);
                break;
            case Down:
            gameGraphics.fillRect(topLeftPixel.x + halfTile - halfLineSize, topLeftPixel.y + halfTile, verticalSize.x, verticalSize.y);
                break;
            default:
                break;
        }
    }

    private static void DrawBacktrackTile(Point pos, Direction dir1, Direction dir2)
    {
        DrawTile(pos, BacktrackTileColor, BacktrackBorderColor);

        DrawDirectionTracker(pos, dir1);
        DrawDirectionTracker(pos, dir2);
    }

    private static void DrawCharacterTile(Point pos, Direction trackerDir) {
        DrawTile(pos, CurrentTileColor, CurrentTileBorderColor);
        DrawDirectionTracker(pos, trackerDir);
        int halfTile = TileSize / 2;
        int halfCharacter = CharacterSize / 2;
        Point topLeftPixel = new Point(pos.x * TileSize + halfTile - halfCharacter + 1,
                pos.y * TileSize + halfTile - halfCharacter + 1);
        gameGraphics.setColor(CharacterColor);
        gameGraphics.fillRect(topLeftPixel.x, topLeftPixel.y, CharacterSize, CharacterSize);
        gameGraphics.setColor(LineColor);
        gameGraphics.drawRect(topLeftPixel.x, topLeftPixel.y, CharacterSize, CharacterSize);
    }

    private static Point DirectionToVec(Direction dir) {
        switch (dir) {
            case Left:
                return Left;
            case Right:
                return Right;
            case Up:
                return Up;
            case Down:
                return Down;
            default:
                return new Point();
        }
    }

    private static Direction ReverseDir(Direction dir) {
        switch (dir) {
            case Left:
                return Direction.Right;
            case Right:
                return Direction.Left;
            case Up:
                return Direction.Down;
            case Down:
                return Direction.Up;
            default:
                return Direction.Down;
        }
    }

    static boolean isInLevelBounds(Point p) {
        LevelData level = getCurrentLevel();
        return p.x >= 0 && p.y >= 0 && p.x < level.size && p.y < level.size;
    }

    static boolean isCurrentLevelSolved() {
        LevelData level = getCurrentLevel();
        return regpos == level.size * level.size - level.numObstacles;
    }

    private static void MoveCharacter(Direction dir) {
        Point dirVec = DirectionToVec(dir);
        Point nextPos = new Point(pos.x + dirVec.x, pos.y + dirVec.y);

        if (!isInLevelBounds(nextPos) || board[nextPos.x][nextPos.y])
            return; // Can't move to this spot

        reg[regpos] = dir;
        regpos++;

        DrawBacktrackTile(pos, dir, prevdir);

        pos = nextPos;
        board[pos.x][pos.y] = true;
        prevdir = ReverseDir(dir);

        DrawCharacterTile(pos, prevdir);

        if (isCurrentLevelSolved()) {
            handleLevelSolved();
        }

        RefreshGameGraphics();
    }

    static void RefreshGameGraphics()
    {
        label.setIcon(new ImageIcon(gameImage));
    }

    static void handleLevelSolved() {
        // TODO
    }

    @Override
    public void focusLost(FocusEvent e) {
        focus = false;
        frame.getContentPane().setCursor(Cursor.getDefaultCursor());
    }

    public fpp() {
        addKeyListener(this);
        addFocusListener(this);
    }

    public void addNotify() {
        super.addNotify();
        requestFocus();
    }

    static void HandleUserKey(UserKey key) {
        switch (key) {
            case LeaveLevel:
                goToWorld(currentWorldIndex);
                return;
            case LeaveWorld:
                goToMainMenu();
                return;
            case Backtrack:
                HandleBacktrack();
                return;
            case Reset:
                goToLevel(currentLevelIndex);
                return;
            case Left:
                MoveCharacter(Direction.Left);
                return;
            case Right:
                MoveCharacter(Direction.Right);
                return;
            case Up:
                MoveCharacter(Direction.Up);
                return;
            case Down:
                MoveCharacter(Direction.Down);
                return;
            default:
                break;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        UserKey currentKey = getUserEventFromKey(e.getKeyCode());
        if (currentKey != UserKey.None)
            HandleUserKey(currentKey);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        UserKey currentKey = getUserEventFromKey(e.getKeyCode());
        if (currentKey != UserKey.None)
            HandleUserKey(currentKey);
    }

    @Override
    public void keyReleased(KeyEvent e) {
    }

    static UserKey getUserEventFromKey(int keyCode) {
        switch (currentScreen) {
            case LevelScreen:
                switch (keyCode) {
                    case KeyEvent.VK_ESCAPE:
                        return UserKey.LeaveLevel;
                    case KeyEvent.VK_R:
                        return UserKey.Reset;
                    case KeyEvent.VK_Q:
                        return UserKey.Backtrack;
                    case KeyEvent.VK_LEFT:
                    case KeyEvent.VK_A:
                        return UserKey.Left;
                    case KeyEvent.VK_RIGHT:
                    case KeyEvent.VK_D:
                        return UserKey.Right;
                    case KeyEvent.VK_UP:
                    case KeyEvent.VK_W:
                        return UserKey.Up;
                    case KeyEvent.VK_DOWN:
                    case KeyEvent.VK_S:
                        return UserKey.Down;
                    default:
                        return UserKey.None;
                }
            case WorldScreen:
                return keyCode == KeyEvent.VK_ESCAPE ? UserKey.LeaveWorld : UserKey.None;
            default:
                return UserKey.None;
        }
    }

    @Override
    public void focusGained(FocusEvent arg0) {
        focus = true;
    }

    public static void loadSaveGame(File file) throws IOException {
        boolean solve;

        if (file.exists()) {
            InputStream os = new FileInputStream(file);
            boolean[] vec = new boolean[8];
            int i = 0;

            for (int l = 0; l < 5; l++) {
                vec = dectobin(os.read());
                for (int k = 0; k < 8; k++) {
                    if (vec[k] && i < 35)
                        psolve[i] = true;
                    i++;
                }
            }
            os.close();

            // TODO remove
            for (int j = 0; j< psolve.length; j++) {
                psolve[j] = true;
            }

            solve = true;
            for (int l = 0; l < 6; l++) {
                if (!psolve[l])
                    solve = false;
            }
            if (solve)
                psolve2[0] = true;
            else
                solve = true;
            for (int l = 6; l < 12; l++) {
                if (!psolve[l])
                    solve = false;
            }
            if (solve)
                psolve2[1] = true;
            else
                solve = true;
            for (int l = 12; l < 24; l++) {
                if (!psolve[l])
                    solve = false;
            }
            if (solve)
                psolve2[2] = true;
            else
                solve = true;
            for (int l = 24; l < 30; l++) {
                if (!psolve[l])
                    solve = false;
            }
            if (solve)
                psolve2[3] = true;
            else
                solve = true;
            for (int l = 30; l < 35; l++) {
                if (!psolve[l])
                    solve = false;
            }
            if (solve)
                psolve2[4] = true;
            else
                solve = true;

            if (psolve2[0] && psolve2[1] && psolve2[2] && psolve2[3] && psolve2[4])
                psolvec = true;

        } else {
            OutputStream os = new FileOutputStream(file);

            boolean[] vec = new boolean[8];

            os.write(bintodec(vec));
            os.write(bintodec(vec));
            os.write(bintodec(vec));
            os.write(bintodec(vec));
            os.write(bintodec(vec));

            os.close();
        }
    }

    public static void savegame(File file) throws IOException {
        OutputStream os = new FileOutputStream(file);

        boolean[] vec = new boolean[8];

        int tmp = 0;

        for (int i = 0; i < 35; i++) {
            if (psolve[i])
                vec[tmp] = true;
            else
                vec[tmp] = false;
            tmp++;
            if (tmp == 8) {
                os.write(bintodec(vec));
                tmp = 0;
            }
        }

        os.write(bintodec(vec));

        os.close();
    }

    public static boolean[] dectobin(int byt) {
        boolean[] ret = new boolean[8];
        int i;
        int tmp = byt;

        for (i = 7; i >= 0; i--) {
            tmp = byt % 2;
            if (tmp == 1)
                ret[i] = true;
            byt -= tmp;
            byt /= 2;
        }

        return ret;
    }

    public static int bintodec(boolean[] vec) {
        int ret = 0;
        int mult = 1;
        int i;

        for (i = 7; i >= 0; i--) {
            if (vec[i])
                ret += mult;
            mult *= 2;
        }
        return ret;
    }

    public static class WorldData {
        public BufferedImage menuImage;
        public Graphics menuGraphics;
        public LevelData[] levels;

        public WorldData()
        {
        }

        public boolean IsSolved() {
            for (LevelData level : levels) {
                if (!level.isSolved)
                    return false;
            }
            return true;
        }
    }

    public static class LevelData implements Serializable {
        public int numObstacles;
        public int size;
        public Point startPos;
        public Point[] obstacles;
        public boolean isSolved;

        public LevelData() {
            isSolved = false;
        }
    }
}
