package com.imposter.mockimposter.service;

import com.imposter.mockimposter.entities.MockProject;
import com.imposter.mockimposter.entities.User;
import com.imposter.mockimposter.repositories.MockProjectRepository;
import com.imposter.mockimposter.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class MockProjectService {

    private static final Logger logger = Logger.getLogger(MockProjectService.class.getName());
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean IS_UNIX = !IS_WINDOWS;

    @Value("${script.py.global.dir:script_py}")
    private String globalScriptPyDirPath;
    private Path globalScriptPyDir;
    @Value("${docker.network:mocknet}")
    private String dockerNetwork;

    @Value("${base.dir:projets_mocks}")
    private String baseDirPath;
    private Path baseDir;

    @Autowired
    private MockProjectRepository mockProjectRepository;

    @Autowired
    private UserRepository userRepository;

    @PostConstruct
    public void init() {
        globalScriptPyDir = Paths.get(globalScriptPyDirPath).toAbsolutePath().normalize();
        baseDir = Paths.get(baseDirPath).toAbsolutePath().normalize();

        try {
            if (!Files.exists(globalScriptPyDir)) Files.createDirectories(globalScriptPyDir);
            if (!Files.exists(baseDir)) Files.createDirectories(baseDir);

            // Créer le réseau Docker s'il n'existe pas
            createDockerNetworkIfNotExists();
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer les dossiers initiaux", e);
        }

        logger.info("[MockProjectService] OS détecté : " + System.getProperty("os.name"));
        logger.info("[MockProjectService] Global script_py dir = " + globalScriptPyDir);
        logger.info("[MockProjectService] Base projects dir = " + baseDir);
    }

    // 🔑 Récupération utilisateur connecté
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Aucun utilisateur connecté");
        }

        String username = authentication.getName();
        if ("anonymousUser".equals(username)) {
            throw new RuntimeException("Utilisateur anonyme - connexion requise");
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé en base : " + username));
    }

    // 🔨 Création projet mock
    @Transactional(rollbackFor = Exception.class)
    public MockProject createMockProject(String projectName, MultipartFile wsdlFile, MultipartFile xsdFile) throws Exception {
        User currentUser = getCurrentUser();

        boolean projectExists = mockProjectRepository.findByUser(currentUser)
                .stream()
                .anyMatch(p -> p.getName().equals(projectName));
        if (projectExists) {
            throw new RuntimeException("Un projet avec le nom '" + projectName + "' existe déjà pour cet utilisateur");
        }

        Path projectPath = baseDir.resolve(projectName);
        if (!Files.exists(projectPath)) {
            Files.createDirectories(projectPath);
            // Fixer les permissions du dossier projet (Linux uniquement)
            fixFilePermissions(projectPath);
        }

        // 📂 Sauvegarde des fichiers
        Path wsdlDest = projectPath.resolve(wsdlFile.getOriginalFilename());
        wsdlFile.transferTo(wsdlDest.toFile());
        fixFilePermissions(wsdlDest);

        Path xsdDest = null;
        if (xsdFile != null && !xsdFile.isEmpty()) {
            Path xsdDir = projectPath.resolve("xsd");
            if (!Files.exists(xsdDir)) Files.createDirectories(xsdDir);
            fixFilePermissions(xsdDir);
            xsdDest = xsdDir.resolve(xsdFile.getOriginalFilename());
            xsdFile.transferTo(xsdDest.toFile());
            fixFilePermissions(xsdDest);
        }

        // 💾 Sauvegarde en base
        MockProject project = new MockProject();
        project.setName(projectName);
        project.setPath(projectPath.toString());
        project.setWsdlPath(wsdlDest.toString());
        project.setXsdPath(xsdDest != null ? xsdDest.toString() : null);
        project.setCreatedAt(LocalDateTime.now());
        project.setUser(currentUser);

        MockProject savedProject = mockProjectRepository.save(project);

        // ⚙️ Exécuter generate.py
        try {
            runPythonScript(
                    globalScriptPyDir.resolve("generate.py"),
                    projectName,
                    wsdlDest,
                    xsdDest,
                    projectPath
            );

            // Fixer les permissions des fichiers générés (Linux uniquement)
            fixProjectPermissions(projectPath);
        } catch (Exception e) {
            logger.severe("[generate.py] Erreur : " + e.getMessage());
        }

        // 🚀 Lancer conteneur docker
        try {
            startDockerContainer(projectName);
        } catch (Exception e) {
            logger.severe("[docker_control.py] Impossible de démarrer conteneur : " + e.getMessage());
        }

        return savedProject;
    }

    // Méthode cross-platform pour fixer les permissions
    private void fixFilePermissions(Path path) {
        try {
            if (IS_UNIX) {
                // Linux/Mac : utiliser POSIX permissions
                Set<PosixFilePermission> perms = Files.isDirectory(path) ?
                        PosixFilePermissions.fromString("rwxrwxrwx") :  // 777 pour dossiers
                        PosixFilePermissions.fromString("rw-rw-rw-");   // 666 pour fichiers

                Files.setPosixFilePermissions(path, perms);
                logger.info("[PERMISSIONS] Permissions POSIX fixées pour : " + path);
            } else {
                // Windows : utiliser les permissions Java standard
                File file = path.toFile();
                file.setReadable(true, false);   // Lecture pour tous
                file.setWritable(true, false);   // Écriture pour tous
                if (Files.isDirectory(path)) {
                    file.setExecutable(true, false); // Exécution pour dossiers
                }
                logger.info("[PERMISSIONS] Permissions Windows fixées pour : " + path);
            }
        } catch (Exception e) {
            logger.warning("[PERMISSIONS] Impossible de fixer les permissions pour " + path + ": " + e.getMessage());
        }
    }

    // Fixer les permissions de tout le projet (cross-platform)
    private void fixProjectPermissions(Path projectPath) throws IOException {
        Files.walkFileTree(projectPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                fixFilePermissions(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                fixFilePermissions(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Créer le réseau Docker s'il n'existe pas (cross-platform)
    private void createDockerNetworkIfNotExists() {
        try {
            // Vérifier si le réseau existe
            String[] dockerCmd = IS_WINDOWS ?
                    new String[]{"cmd", "/c", "docker", "network", "ls", "-q", "-f", "name=" + dockerNetwork} :
                    new String[]{"docker", "network", "ls", "-q", "-f", "name=" + dockerNetwork};

            ProcessBuilder pb = new ProcessBuilder(dockerCmd);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(java.util.stream.Collectors.joining("\n")).trim();
            }

            if (output.isEmpty()) {
                // Créer le réseau
                logger.info("[DOCKER] Création du réseau Docker : " + dockerNetwork);
                String[] createCmd = IS_WINDOWS ?
                        new String[]{"cmd", "/c", "docker", "network", "create", dockerNetwork} :
                        new String[]{"docker", "network", "create", dockerNetwork};

                ProcessBuilder createPb = new ProcessBuilder(createCmd);
                Process createProcess = createPb.start();
                createProcess.waitFor();
                logger.info("[DOCKER] Réseau " + dockerNetwork + " créé avec succès");
            } else {
                logger.info("[DOCKER] Réseau " + dockerNetwork + " existe déjà");
            }
        } catch (Exception e) {
            logger.warning("[DOCKER] Erreur lors de la création du réseau : " + e.getMessage());
        }
    }

    // --- Exécution script Python générique (cross-platform) ---
    private void runPythonScript(Path scriptPath, String projectName, Path wsdlPath, Path xsdPath, Path outputDir) throws Exception {
        if (!scriptPath.toFile().exists()) {
            throw new RuntimeException("Script Python introuvable : " + scriptPath);
        }

        String pythonCommand = detectPython();
        List<String> command = new java.util.ArrayList<>();

        if (IS_WINDOWS) {
            command.add("cmd");
            command.add("/c");
        }

        command.add(pythonCommand);
        command.add(scriptPath.toString());
        command.add("--project=" + projectName);
        command.add("--wsdl=" + wsdlPath.toAbsolutePath().toString());
        if (xsdPath != null) {
            command.add("--xsd=" + xsdPath.toAbsolutePath().toString());
        }
        command.add("--output=" + outputDir.toAbsolutePath().toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(scriptPath.getParent().toFile());
        pb.redirectErrorStream(true);

        // Ajouter la variable BASE_DIR pour le script Python
        pb.environment().put("BASE_DIR", baseDir.toString());

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[PYTHON] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Script Python terminé avec code d'erreur " + exitCode);
        }
    }

    private String detectPython() {
        String[] candidates = IS_WINDOWS ?
                new String[]{"python.exe", "python", "py.exe", "py"} :
                new String[]{"python3", "python"};

        for (String cmd : candidates) {
            try {
                String[] testCmd = IS_WINDOWS ?
                        new String[]{"cmd", "/c", cmd, "--version"} :
                        new String[]{cmd, "--version"};

                ProcessBuilder pb = new ProcessBuilder(testCmd);
                Process p = pb.start();
                if (p.waitFor() == 0) {
                    logger.info("[PYTHON] Interpréteur Python détecté : " + cmd);
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("Aucun interpréteur Python trouvé dans le PATH.");
    }

    // --- Gestion projets ---
    @Transactional(readOnly = true)
    public List<MockProject> getProjectsByCurrentUser() {
        return mockProjectRepository.findByUser(getCurrentUser());
    }

    @Transactional
    public void deleteProject(MockProject project) throws IOException {
        Path projectDir = Paths.get(project.getPath());
        if (Files.exists(projectDir)) {
            deleteDirectoryRecursively(projectDir.toFile());
        }
        mockProjectRepository.delete(project);

        try {
            stopDockerContainer(project.getName());
        } catch (Exception e) {
            logger.warning("[docker_control.py] Erreur lors de l'arrêt du conteneur : " + e.getMessage());
        }
    }

    private void deleteDirectoryRecursively(File dir) throws IOException {
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                if (file.isDirectory()) deleteDirectoryRecursively(file);
                else Files.delete(file.toPath());
            }
        }
        Files.delete(dir.toPath());
    }

    @Transactional(readOnly = true)
    public List<MockProject> getAllProjects() {
        return mockProjectRepository.findAll();
    }

    // --- Gestion Docker AMÉLIORÉE (cross-platform) ---
    public void startDockerContainer(String projectName) throws IOException, InterruptedException {
        logger.info("[DOCKER] Démarrage conteneur pour projet : " + projectName);
        runDockerScript("start", projectName);

        // Attendre plus longtemps pour le démarrage
        logger.info("[DOCKER] Attente de démarrage du conteneur...");
        Thread.sleep(10000); // 10 secondes

        if (!isContainerRunning(projectName)) {
            String logs = getContainerLogs(projectName);
            logger.severe("[DOCKER] Logs du conteneur arrêté : " + logs);
            throw new RuntimeException("Le conteneur s'est arrêté après le démarrage. Logs: " + logs);
        }

        logger.info("[DOCKER] Conteneur démarré avec succès : mock-" + projectName);
    }

    public void stopDockerContainer(String projectName) throws IOException, InterruptedException {
        logger.info("[DOCKER] Arrêt conteneur pour projet : " + projectName);
        runDockerScript("stop", projectName);
    }

    private void runDockerScript(String action, String projectName) throws IOException, InterruptedException {
        if (!Files.exists(globalScriptPyDir) || !Files.isDirectory(globalScriptPyDir)) {
            throw new IllegalStateException("Dossier script_py introuvable : " + globalScriptPyDir);
        }

        Path dockerScript = globalScriptPyDir.resolve("docker_control.py");
        if (!Files.exists(dockerScript)) {
            throw new RuntimeException("Script docker_control.py introuvable : " + dockerScript);
        }

        String pythonCommand = detectPython();
        List<String> command = new java.util.ArrayList<>();

        if (IS_WINDOWS) {
            command.add("cmd");
            command.add("/c");
        }

        command.add(pythonCommand);
        command.add("docker_control.py");
        command.add(action);
        command.add(projectName);

        // Ajouter le port si nécessaire
        if ("start".equals(action)) {
            command.add("--port=8080");
        }

        logger.info("[DOCKER] Commande : " + String.join(" ", command));
        logger.info("[DOCKER] Répertoire de travail : " + globalScriptPyDir);
        logger.info("[DOCKER] BASE_DIR : " + baseDir.toString());
        logger.info("[DOCKER] NETWORK : " + dockerNetwork);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(globalScriptPyDir.toFile());
        pb.redirectErrorStream(true);

        // Injection des variables d'environnement
        pb.environment().put("BASE_DIR", baseDir.toString());
        pb.environment().put("DOCKER_NETWORK", dockerNetwork);

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[DOCKER] " + line);
                output.append(line).append("\n");
            }
        }

        boolean finished = process.waitFor(120, TimeUnit.SECONDS); // 2 minutes

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Timeout lors de l'exécution du script Docker (" + action + ")");
        }

        int exitCode = process.exitValue();
        logger.info("[DOCKER] Code de sortie : " + exitCode);

        if (exitCode != 0) {
            throw new RuntimeException("Échec du script Docker (" + action + "), code=" + exitCode +
                    ", sortie: " + output.toString());
        }
    }

    // Méthodes utilitaires pour vérifier l'état des conteneurs (cross-platform)
    private boolean isContainerRunning(String projectName) {
        try {
            String containerName = "mock-" + projectName;
            String[] cmd = IS_WINDOWS ?
                    new String[]{"cmd", "/c", "docker", "ps", "-q", "-f", "name=" + containerName} :
                    new String[]{"docker", "ps", "-q", "-f", "name=" + containerName};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines()
                        .collect(java.util.stream.Collectors.joining("\n"))
                        .trim();
            }

            boolean running = !output.isEmpty();
            logger.info("[DOCKER] Conteneur " + containerName + " en cours : " + running);
            return running;

        } catch (Exception e) {
            logger.warning("[DOCKER] Erreur vérification conteneur : " + e.getMessage());
            return false;
        }
    }

    private String getContainerLogs(String projectName) {
        try {
            String containerName = "mock-" + projectName;
            String[] cmd = IS_WINDOWS ?
                    new String[]{"cmd", "/c", "docker", "logs", "--tail", "50", containerName} :
                    new String[]{"docker", "logs", "--tail", "50", containerName};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            StringBuilder logs = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append(line).append("\n");
                }
            }

            // Lire aussi stderr
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logs.append("ERROR: ").append(line).append("\n");
                }
            }

            return logs.toString();
        } catch (Exception e) {
            return "Impossible de récupérer les logs : " + e.getMessage();
        }
    }

    // Méthode pour obtenir l'état d'un conteneur (cross-platform)
    public String getContainerStatus(String projectName) {
        try {
            String containerName = "mock-" + projectName;
            String[] cmd = IS_WINDOWS ?
                    new String[]{"cmd", "/c", "docker", "ps", "-a", "-f", "name=" + containerName, "--format", "{{.Status}}"} :
                    new String[]{"docker", "ps", "-a", "-f", "name=" + containerName, "--format", "{{.Status}}"};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                return reader.lines()
                        .findFirst()
                        .orElse("Conteneur non trouvé");
            }
        } catch (Exception e) {
            return "Erreur : " + e.getMessage();
        }
    }
}