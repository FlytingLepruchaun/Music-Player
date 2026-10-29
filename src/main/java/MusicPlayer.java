import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import javax.sound.sampled.*;
import java.sql.*;

public class MusicPlayer {

    private static class Song {
        public int id;
        public String title;
        public String filePath;

        public Song(int id, String title, String filePath) {
            this.id = id;
            this.title = title;
            this.filePath = filePath;
        }

        @Override
        public String toString() {
            return title;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Song song = (Song) obj;
            return id == song.id;
        }
    }
    private static class Playlist {
        public int id;
        public int userId;
        public String name;

        public Playlist(int id, int userId, String name) {
            this.id = id;
            this.userId = userId;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static class DatabaseManager {
        private static final String JDBC_DRIVER = "org.sqlite.JDBC";
        private static final String DB_URL = "jdbc:sqlite:musicplayer.db";

        public DatabaseManager() {
            try {
                Class.forName(JDBC_DRIVER);
                initializeDatabase();
            } catch (ClassNotFoundException e) {
                JOptionPane.showMessageDialog(null, "SQLite JDBC Driver not found. Ensure the JAR is in the classpath.", "Driver Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }

        private void initializeDatabase() {
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {

                stmt.execute("PRAGMA foreign_keys = ON;");

                stmt.execute("CREATE TABLE IF NOT EXISTS users ("
                        + "id INTEGER PRIMARY KEY,"
                        + "username TEXT NOT NULL UNIQUE,"
                        + "password TEXT NOT NULL);");

                stmt.execute("CREATE TABLE IF NOT EXISTS songs ("
                        + "id INTEGER PRIMARY KEY,"
                        + "title TEXT NOT NULL,"
                        + "file_path TEXT NOT NULL UNIQUE);");

                stmt.execute("CREATE TABLE IF NOT EXISTS playlists ("
                        + "id INTEGER PRIMARY KEY,"
                        + "name TEXT NOT NULL,"
                        + "user_id INTEGER NOT NULL,"
                        + "FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE);");

                stmt.execute("CREATE TABLE IF NOT EXISTS playlist_songs ("
                        + "playlist_id INTEGER NOT NULL,"
                        + "song_id INTEGER NOT NULL,"
                        + "PRIMARY KEY(playlist_id, song_id),"
                        + "FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,"
                        + "FOREIGN KEY(song_id) REFERENCES songs(id) ON DELETE CASCADE);");

                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                    if (rs.next() && rs.getInt(1) == 0) {
                        insertInitialData(conn);
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Database initialization failed: " + e.getMessage(),
                        "DB Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void insertInitialData(Connection conn) throws SQLException {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("INSERT OR IGNORE INTO users (username, password) VALUES ('test', 'pass')");
            }

//            String insertSongSql = "INSERT OR IGNORE INTO songs (title, file_path) VALUES (?, ?)";
//            try (PreparedStatement pstmt = conn.prepareStatement(insertSongSql)) {
//                pstmt.setString(1, "Global Track 1: Welcome");
//                pstmt.setString(2, "01 - welcome_track.wav");
//                pstmt.executeUpdate();
//
//                pstmt.setString(1, "Global Track 2: Sample");
//                pstmt.setString(2, "sample_audio.wav");
//                pstmt.executeUpdate();
//
//                pstmt.setString(1, "Global Track 3: Finale");
//                pstmt.setString(2, "03 - finale_track.wav");
//                pstmt.executeUpdate();
//            }
        }

        public boolean addSongToLibrary(String title, String filePath) {
            String sql = "INSERT INTO songs (title, file_path) VALUES (?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, title);
                pstmt.setString(2, filePath);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(null, "Could not add song. File path may already exist.", "Database Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
                return false;
            }
        }

        public int authenticateUser(String username, String password) {
            String sql = "SELECT id FROM users WHERE username = ? AND password = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("id");
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return -1;
        }
        public boolean createUser(String username, String password) {
            String sql = "INSERT INTO users (username, password) VALUES (?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        public List<Song> loadGlobalSongs() {
            List<Song> songs = new ArrayList<>();
            String sql = "SELECT id, title, file_path FROM songs";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    songs.add(new Song(rs.getInt("id"), rs.getString("title"), rs.getString("file_path")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return songs;
        }

        public List<Playlist> loadUserPlaylists(int userId) {
            List<Playlist> playlists = new ArrayList<>();
            String sql = "SELECT id, name FROM playlists WHERE user_id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    playlists.add(new Playlist(rs.getInt("id"), userId, rs.getString("name")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return playlists;
        }
        public List<Song> loadSongsForPlaylist(int playlistId) {
            List<Song> songs = new ArrayList<>();
            String sql = "SELECT s.id, s.title, s.file_path FROM songs s "
                    + "JOIN playlist_songs ps ON s.id = ps.song_id "
                    + "WHERE ps.playlist_id = ?";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, playlistId);
                ResultSet rs = pstmt.executeQuery();
                while (rs.next()) {
                    songs.add(new Song(rs.getInt("id"), rs.getString("title"), rs.getString("file_path")));
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return songs;
        }

        public Playlist createPlaylist(int userId, String name) {
            String sql = "INSERT INTO playlists (name, user_id) VALUES (?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql,
                         Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, name);
                pstmt.setInt(2, userId);
                int affectedRows = pstmt.executeUpdate();
                if (affectedRows > 0) {
                    try (ResultSet rs = pstmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            return new Playlist(rs.getInt(1), userId, name);
                        }
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        }

        public boolean addSongToPlaylist(int playlistId, int songId) {
            String sql = "INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id) VALUES (?, ?)";
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, playlistId);
                pstmt.setInt(2, songId);
                pstmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    private final DatabaseManager dbManager;
    private int currentUserId = -1;
    private String currentUsername = "";

    private final JFrame frame;
    private final CardLayout cardLayout;
    private final JPanel mainPanel;

    private Clip audioClip;
    private boolean isPlaying = false;
    private Song currentPlayingSong = null;
    private final LinkedList<Song> playbackQueue = new LinkedList<>();

    private final JLabel currentTrackLabel = new JLabel("Now Playing: Nothing");
    private final JLabel welcomeUserLabel = new JLabel();
    private final JComboBox<Playlist> playlistComboBox = new JComboBox<>();
    private final DefaultListModel<Song> playlistSongModel = new DefaultListModel<>();
    private final JList<Song> playlistSongView = new JList<>(playlistSongModel);
    private final DefaultListModel<Song> globalSongModel = new DefaultListModel<>();
    private final JList<Song> globalSongView = new JList<>(globalSongModel);
    private final DefaultListModel<Song> queueModel = new DefaultListModel<>();
    private final JList<Song> queueView = new JList<>(queueModel);
    private final JButton playPauseButton = new JButton("Play");

    public MusicPlayer() {
        dbManager = new DatabaseManager();

        frame = new JFrame("Multi-User Music Player");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 700);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        JPanel loginPanel = createLoginPanel();
        JPanel registerPanel = createRegisterPanel();
        JPanel mainPlayerPanel = createMainPlayerPanel();

        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(registerPanel, "REGISTER");
        mainPanel.add(mainPlayerPanel, "PLAYER");

        frame.add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN");

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Music Player Login", SwingConstants.CENTER), gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        JTextField userField = new JTextField(20);
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        JPasswordField passField = new JPasswordField(20);
        panel.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        JButton loginButton = new JButton("Login");
        panel.add(loginButton, gbc);

        gbc.gridx = 0; gbc.gridy = 4;
        JButton registerButton = new JButton("Go to Register");
        panel.add(registerButton, gbc);

        loginButton.addActionListener(e -> {
            String user = userField.getText();
            String pass = new String(passField.getPassword());
            int userId = dbManager.authenticateUser(user, pass);
            if (userId != -1) {
                currentUserId = userId;
                currentUsername = user;
                onLoginSuccess();
            } else {
                JOptionPane.showMessageDialog(panel, "Invalid username or password.", "Login Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        registerButton.addActionListener(e -> cardLayout.show(mainPanel, "REGISTER"));

        return panel;
    }

    private JPanel createRegisterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        panel.add(new JLabel("Create New User", SwingConstants.CENTER), gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1;
        panel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1; gbc.gridy = 1;
        JTextField userField = new JTextField(20);
        panel.add(userField, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 2;
        JPasswordField passField = new JPasswordField(20);
        panel.add(passField, gbc);
        gbc.gridx = 0; gbc.gridy = 3;
        panel.add(new JLabel("Confirm Password:"), gbc);
        gbc.gridx = 1; gbc.gridy = 3;
        JPasswordField confirmPassField = new JPasswordField(20);
        panel.add(confirmPassField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        JButton registerButton = new JButton("Register");
        panel.add(registerButton, gbc);

        gbc.gridx = 0; gbc.gridy = 6;
        JButton loginButton = new JButton("Back to Login");
        panel.add(loginButton, gbc);

        registerButton.addActionListener(e -> {
            String user = userField.getText();
            String pass = new String(passField.getPassword());
            String confirmPass = new String(confirmPassField.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Username and password cannot be empty.",
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!pass.equals(confirmPass)) {
                JOptionPane.showMessageDialog(panel, "Passwords do not match.", "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (dbManager.createUser(user, pass)) {
                JOptionPane.showMessageDialog(panel, "User created successfully! Please login.",
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                cardLayout.show(mainPanel, "LOGIN");
            } else {
                JOptionPane.showMessageDialog(panel, "Username already exists.", "Registration Failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        loginButton.addActionListener(e -> cardLayout.show(mainPanel, "LOGIN"));

        return panel;
    }
    private JPanel createMainPlayerPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(welcomeUserLabel, BorderLayout.WEST);
        currentTrackLabel.setFont(new Font("Arial", Font.BOLD, 16));
        currentTrackLabel.setHorizontalAlignment(SwingConstants.CENTER);
        headerPanel.add(currentTrackLabel, BorderLayout.CENTER);
        JButton logoutButton = new JButton("Logout");
        headerPanel.add(logoutButton, BorderLayout.EAST);
        panel.add(headerPanel, BorderLayout.NORTH);

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createPlaylistPanel(),
                createLibraryAndQueuePanel());
        centerSplit.setDividerLocation(300);
        panel.add(centerSplit, BorderLayout.CENTER);

        panel.add(createControlsPanel(), BorderLayout.SOUTH);

        logoutButton.addActionListener(e -> handleLogout());
        playlistComboBox.addActionListener(e -> loadSongsForSelectedPlaylist());

        return panel;
    }

    private JPanel createPlaylistPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("My Playlists"));

        JPanel comboPanel = new JPanel(new BorderLayout());
        comboPanel.add(playlistComboBox, BorderLayout.CENTER);
        JButton createPlaylistButton = new JButton("New Playlist");
        comboPanel.add(createPlaylistButton, BorderLayout.EAST);
        panel.add(comboPanel, BorderLayout.NORTH);

        panel.add(new JScrollPane(playlistSongView), BorderLayout.CENTER);

        createPlaylistButton.addActionListener(e -> handleCreatePlaylist());

        return panel;
    }
    private Component createLibraryAndQueuePanel() {
        JSplitPane libQueueSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createLibraryPanel(),
                createQueuePanel());
        libQueueSplit.setDividerLocation(300);
        return libQueueSplit;
    }

    private JPanel createLibraryPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Global Song Library"));

        panel.add(new JScrollPane(globalSongView), BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton addSongToLibraryButton = new JButton("Add New Song");
        JButton addSongToPlaylistButton = new JButton("Add to Playlist");
        buttonPanel.add(addSongToLibraryButton);
        buttonPanel.add(addSongToPlaylistButton);

        panel.add(buttonPanel, BorderLayout.SOUTH);

        addSongToPlaylistButton.addActionListener(e -> handleAddSongToPlaylist());
        addSongToLibraryButton.addActionListener(e -> handleAddNewSongToLibrary());

        return panel;
    }

    private JPanel createQueuePanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Playback Queue"));
        panel.add(new JScrollPane(queueView), BorderLayout.CENTER);

        JButton addToQueueButton = new JButton("Add to Queue");
        panel.add(addToQueueButton, BorderLayout.SOUTH);

        addToQueueButton.addActionListener(e -> handleAddToQueue());

        return panel;
    }

    private JPanel createControlsPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 15));
        controlPanel.setBackground(new Color(240, 240, 240));

        JButton prevButton = new JButton("<< Previous");
        JButton stopButton = new JButton("Stop");
        JButton nextButton = new JButton("Next >>");

        JButton[] buttons = {prevButton, playPauseButton, stopButton, nextButton};
        for (JButton btn : buttons) {
            btn.setFocusPainted(false);
            btn.setPreferredSize(new Dimension(110, 35));
        }

        controlPanel.add(prevButton);
        controlPanel.add(playPauseButton);
        controlPanel.add(stopButton);
        controlPanel.add(nextButton);

        playPauseButton.addActionListener(e -> handlePlayPause());
        stopButton.addActionListener(e -> handleStop());
        nextButton.addActionListener(e -> playNextTrack(true));
        prevButton.addActionListener(e -> handlePrevious());

        return controlPanel;
    }

    private void handleAddNewSongToLibrary() {
        FileDialog fd = new FileDialog(frame, "Select a .wav file", FileDialog.LOAD);
        fd.setFile("*.wav");
        fd.setVisible(true);

        if (fd.getFile() != null) {
            String filePath = fd.getDirectory() + fd.getFile();
            String title = JOptionPane.showInputDialog(frame, "Enter a title for this song:", "Add New Song", JOptionPane.PLAIN_MESSAGE);

            if (title != null && !title.trim().isEmpty()) {
                if (dbManager.addSongToLibrary(title.trim(), filePath)) {
                    JOptionPane.showMessageDialog(frame, "Song added to library!", "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    loadGlobalSongs();
                }
            }
        }
    }
    private void onLoginSuccess() {
        welcomeUserLabel.setText("Welcome, " + currentUsername + "!");
        loadUserPlaylists();
        loadGlobalSongs();
        updateQueueUI();
        cardLayout.show(mainPanel, "PLAYER");
    }

    private void handleLogout() {
        handleStop();
        currentUserId = -1;
        currentUsername = "";
        currentPlayingSong = null;
        playbackQueue.clear();
        playlistComboBox.removeAllItems();
        playlistSongModel.clear();
        globalSongModel.clear();
        queueModel.clear();
        cardLayout.show(mainPanel, "LOGIN");
    }

    private void loadUserPlaylists() {
        playlistComboBox.removeAllItems();
        List<Playlist> playlists = dbManager.loadUserPlaylists(currentUserId);
        for (Playlist p : playlists) {
            playlistComboBox.addItem(p);
        }
    }

    private void loadGlobalSongs() {
        globalSongModel.clear();
        List<Song> songs = dbManager.loadGlobalSongs();
        for (Song s : songs) {
            globalSongModel.addElement(s);
        }
    }

    private void loadSongsForSelectedPlaylist() {
        Playlist selectedPlaylist = (Playlist) playlistComboBox.getSelectedItem();
        playlistSongModel.clear();
        if (selectedPlaylist != null) {
            List<Song> songs = dbManager.loadSongsForPlaylist(selectedPlaylist.id);
            for (Song s : songs) {
                playlistSongModel.addElement(s);
            }
        }
    }

    private void handleCreatePlaylist() {
        String name = JOptionPane.showInputDialog(frame, "Enter playlist name:", "New Playlist",
                JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            Playlist newPlaylist = dbManager.createPlaylist(currentUserId, name.trim());
            if (newPlaylist != null) {
                playlistComboBox.addItem(newPlaylist);
                playlistComboBox.setSelectedItem(newPlaylist);
            } else {
                JOptionPane.showMessageDialog(frame, "Could not create playlist.", "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleAddSongToPlaylist() {
        Song selectedSong = globalSongView.getSelectedValue();
        Playlist selectedPlaylist = (Playlist) playlistComboBox.getSelectedItem();

        if (selectedSong == null) {
            JOptionPane.showMessageDialog(frame, "Select a song from the Global Library.", "Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedPlaylist == null) {
            JOptionPane.showMessageDialog(frame, "Select a playlist to add to.", "Error",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (dbManager.addSongToPlaylist(selectedPlaylist.id, selectedSong.id)) {
            JOptionPane.showMessageDialog(frame, "Song added to " + selectedPlaylist.name,
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            Playlist currentlySelected = (Playlist) playlistComboBox.getSelectedItem();
            if (currentlySelected != null && currentlySelected.id == selectedPlaylist.id) {
                loadSongsForSelectedPlaylist();
            }
        } else {
            JOptionPane.showMessageDialog(frame, "Failed to add song.", "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleAddToQueue() {
        Song selectedSong = null;
        if (globalSongView.isFocusOwner() && globalSongView.getSelectedValue() != null) {
            selectedSong = globalSongView.getSelectedValue();
        } else if (playlistSongView.isFocusOwner() && playlistSongView.getSelectedValue() != null)
        {
            selectedSong = playlistSongView.getSelectedValue();
        }

        if (selectedSong != null) {
            playbackQueue.addLast(selectedSong);
            updateQueueUI();
        } else {
            JOptionPane.showMessageDialog(frame, "Select a song from the Library or a Playlist to add to the queue.", "No Song Selected", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void updateQueueUI() {
        queueModel.clear();
        for (Song s : playbackQueue) {
            queueModel.addElement(s);
        }
    }

    private void playTrack(Song song) {
        if (song == null) return;

        handleStop();
        currentPlayingSong = song;

        try {
            File audioFile = new File(song.filePath);
            if (!audioFile.exists()) {
                throw new Exception("File not found: " + song.filePath);
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            audioClip = AudioSystem.getClip();
            audioClip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    if (isPlaying && audioClip.getFramePosition() >= audioClip.getFrameLength()) {
                        SwingUtilities.invokeLater(() -> playNextTrack(true));
                    }
                    isPlaying = false;
                    updateUI();
                }
            });

            audioClip.open(audioStream);
            audioClip.start();
            isPlaying = true;

        } catch (Exception ex) {
            ex.printStackTrace();
            isPlaying = false;
            currentPlayingSong = null;
            JOptionPane.showMessageDialog(frame,
                    "Could not play track: " + song.title + ".\nError: " + ex.getMessage(),
                    "Playback Error", JOptionPane.ERROR_MESSAGE);
        }
        updateUI();
    }

    private void playNextTrack(boolean advancePlaylist) {
        Song nextTrack = null;
        if (!playbackQueue.isEmpty()) {
            nextTrack = playbackQueue.pollFirst();
        } else if (advancePlaylist && playlistSongModel.getSize() > 0) {
            int currentIndex = playlistSongView.getSelectedIndex();
            int nextIndex = (currentIndex + 1) % playlistSongModel.getSize();
            playlistSongView.setSelectedIndex(nextIndex);
            nextTrack = playlistSongModel.getElementAt(nextIndex);
        }

        if (nextTrack != null) {
            playTrack(nextTrack);
        } else {
            handleStop();
        }
    }

    private void handlePrevious() {
        handleStop();
        if (playlistSongModel.getSize() > 0) {
            int currentIndex = playlistSongView.getSelectedIndex();
            int prevIndex = (currentIndex - 1 + playlistSongModel.getSize()) %
                    playlistSongModel.getSize();
            playlistSongView.setSelectedIndex(prevIndex);
            playTrack(playlistSongModel.getElementAt(prevIndex));
        }
    }

    private void handlePlayPause() {
        if (isPlaying) {
            pauseTrack();
        } else {
            if (audioClip != null && !audioClip.isRunning() && currentPlayingSong != null) {
                resumeTrack();
            } else {
                Song songToPlay = currentPlayingSong;

                if (playlistSongView.isFocusOwner() && playlistSongView.getSelectedValue() != null) {
                    songToPlay = playlistSongView.getSelectedValue();
                } else if (globalSongView.isFocusOwner() && globalSongView.getSelectedValue() !=
                        null) {
                    songToPlay = globalSongView.getSelectedValue();
                }

                if (songToPlay == null && playlistSongModel.getSize() > 0) {
                    songToPlay = playlistSongModel.getElementAt(0);
                    playlistSongView.setSelectedIndex(0);
                }
                playTrack(songToPlay);
            }
        }
    }

    private void pauseTrack() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
            isPlaying = false;
        }
        updateUI();
    }

    private void resumeTrack() {
        if (audioClip != null && !audioClip.isRunning()) {
            audioClip.start();
            isPlaying = true;
        }
        updateUI();
    }

    private void handleStop() {
        if (audioClip != null) {
            if (audioClip.isRunning()) {
                audioClip.stop();
            }
            audioClip.setFramePosition(0);
            isPlaying = false;
            currentPlayingSong = null;
        }
        updateUI();
    }

    private void updateUI() {
        if (currentPlayingSong != null) {
            currentTrackLabel.setText("Now Playing: " + currentPlayingSong.title);
        } else {
            currentTrackLabel.setText("Now Playing: Nothing");
        }

        if (isPlaying) {
            playPauseButton.setText("Pause");
        } else {
            playPauseButton.setText("Play");
        }

        updateQueueUI();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MusicPlayer::new);
    }
}