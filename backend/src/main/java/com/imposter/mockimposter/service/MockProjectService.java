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
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Service
public class MockProjectService {

    private static final Logger logger = Logger.getLogger(MockProjectService.class.getName());

    @Value("${script.py.global.dir:script_py}")
    private String globalScriptPyDirPath;
    private Path globalScriptPyDir;

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
        } catch (IOException e) {
            throw new RuntimeException("Impossible de créer les dossiers initiaux", e);
        }

        logger.info("[MockProjectService] Global script_py dir = " + globalScriptPyDir);
        logger.info("[MockProjectService] Base projects dir = " + baseDir);
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            logger.severe("[ERROR] Aucune authentification trouvée");
            throw new RuntimeException("Aucun utilisateur connecté");
        }

        String username = authentication.getName();
        logger.info("[INFO] Nom utilisateur depuis authentification : " + username);

        if ("anonymousUser".equals(username)) {
            logger.severe("[ERROR] Utilisateur anonyme détecté");
            throw new RuntimeException("Utilisateur anonyme - connexion requise");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    logger.severe("[ERROR] Utilisateur non trouvé en base : " + username);
                    return new RuntimeException("Utilisateur non trouvé en base : " + username);
                });

        logger.info("[INFO] Utilisateur trouvé en base : ID=" + user.getId() + ", Username=" + user.getUsername());
        return user;
    }

    @Transactional(rollbackFor = Exception.class)
    public MockProject createMockProject(String projectName, MultipartFile wsdlFile, MultipartFile xsdFile) throws Exception {
        logger.info("=== DÉBUT CRÉATION PROJET : " + projectName + " ===");

        try {
            // 1. Récupérer l'utilisateur AVANT toute opération
            User currentUser = getCurrentUser();
            logger.info("[STEP 1] ✅ Utilisateur récupéré : " + currentUser.getUsername() + " (ID: " + currentUser.getId() + ")");

            // 2. Vérifier que le nom du projet n'existe pas déjà pour cet utilisateur
            boolean projectExists = mockProjectRepository.findByUser(currentUser)
                    .stream()
                    .anyMatch(p -> p.getName().equals(projectName));

            if (projectExists) {
                throw new RuntimeException("Un projet avec le nom '" + projectName + "' existe déjà pour cet utilisateur");
            }

            // 3. Créer le dossier du projet
            Path projectPath = baseDir.resolve(projectName);
            logger.info("[STEP 2] Création dossier projet : " + projectPath);
            if (!Files.exists(projectPath)) {
                Files.createDirectories(projectPath);
                logger.info("[STEP 2] ✅ Dossier créé : " + projectPath);
            }

            // 4. Copier WSDL
            Path wsdlDest = projectPath.resolve(wsdlFile.getOriginalFilename());
            wsdlFile.transferTo(wsdlDest.toFile());
            logger.info("[STEP 3] ✅ WSDL copié : " + wsdlDest);

            // 5. Copier XSD si fourni
            Path xsdDest = null;
            if (xsdFile != null && !xsdFile.isEmpty()) {
                Path xsdDir = projectPath.resolve("xsd");
                if (!Files.exists(xsdDir)) Files.createDirectories(xsdDir);
                xsdDest = xsdDir.resolve(xsdFile.getOriginalFilename());
                xsdFile.transferTo(xsdDest.toFile());
                logger.info("[STEP 4] ✅ XSD copié : " + xsdDest);
            } else {
                logger.info("[STEP 4] ⚠️ Aucun fichier XSD fourni");
            }

            // 6. Créer l'objet projet AVANT l'exécution du script Python
            MockProject project = new MockProject();
            project.setName(projectName);
            project.setPath(projectPath.toString());
            project.setWsdlPath(wsdlDest.toString());
            project.setXsdPath(xsdDest != null ? xsdDest.toString() : null);
            project.setCreatedAt(LocalDateTime.now());
            project.setUser(currentUser);

            logger.info("[STEP 5] Objet MockProject créé - Name: " + project.getName() +
                    ", User: " + project.getUser().getUsername() +
                    ", Path: " + project.getPath());

            // 7. Sauvegarder en base de données AVANT le script Python
            MockProject savedProject;
            try {
                savedProject = mockProjectRepository.save(project);
                logger.info("[STEP 6] ✅ Projet sauvegardé en base avec ID=" + savedProject.getId());

                // Vérifier immédiatement la sauvegarde
                if (savedProject.getId() == null) {
                    throw new RuntimeException("Erreur: Le projet n'a pas reçu d'ID après sauvegarde");
                }

                // Vérifier que le projet est bien en base
                MockProject verifyProject = mockProjectRepository.findById(savedProject.getId()).orElse(null);
                if (verifyProject == null) {
                    throw new RuntimeException("Erreur: Le projet n'est pas trouvé en base après sauvegarde");
                }
                logger.info("[STEP 6] ✅ Vérification: Projet bien présent en base");

            } catch (Exception e) {
                logger.severe("[STEP 6] ❌ ERREUR lors de la sauvegarde en base : " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("Erreur lors de la sauvegarde du projet en base de données", e);
            }

            // 8. Exécuter script Python pour générer les fichiers
            try {
                logger.info("[STEP 7] Exécution script Python...");
                runPythonScript(
                        globalScriptPyDir.resolve("generate.py"),
                        projectName,
                        wsdlDest,
                        xsdDest,
                        projectPath
                );
                logger.info("[STEP 7] ✅ Script Python exécuté avec succès");
            } catch (Exception e) {
                logger.severe("[STEP 7] ❌ ERREUR script Python : " + e.getMessage());
                // Le projet est déjà sauvé en base, on peut continuer ou décider de rollback
                // Pour l'instant, on continue car les fichiers de base sont copiés
                logger.warning("[STEP 7] ⚠️ Projet sauvé en base malgré l'erreur du script Python");
            }

            logger.info("=== ✅ CRÉATION PROJET TERMINÉE AVEC SUCCÈS ===");
            return savedProject;

        } catch (Exception e) {
            logger.severe("=== ❌ ERREUR LORS DE LA CRÉATION DU PROJET ===");
            logger.severe("Erreur : " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-lancer pour déclencher le rollback
        }
    }

    // Appel au script Python de génération
    private void runPythonScript(Path scriptPath, String projectName, Path wsdlPath, Path xsdPath, Path outputDir) throws Exception {
        if (!scriptPath.toFile().exists()) {
            throw new RuntimeException("Script Python introuvable : " + scriptPath);
        }

        String pythonCommand = detectPython();
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(pythonCommand);
        command.add(scriptPath.toString());
        command.add("--project=" + projectName);
        command.add("--wsdl=" + wsdlPath.toAbsolutePath().toString());
        if (xsdPath != null) {
            command.add("--xsd=" + xsdPath.toAbsolutePath().toString());
        }
        command.add("--output=" + outputDir.toAbsolutePath().toString());

        logger.info("[PYTHON] Commande : " + String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(scriptPath.getParent().toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[PYTHON] " + line);
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            String errorMsg = "Script Python terminé avec code d'erreur " + exitCode +
                    "\nSortie du script:\n" + output.toString();
            logger.severe("[PYTHON] " + errorMsg);
            throw new RuntimeException(errorMsg);
        }

        logger.info("[PYTHON] ✅ Script terminé avec succès");
    }

    private String detectPython() {
        String[] candidates = System.getProperty("os.name").toLowerCase().contains("win") ?
                new String[] {"python.exe", "python"} :
                new String[] {"python3", "python"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                Process p = pb.start();
                int exitCode = p.waitFor();
                if (exitCode == 0) {
                    logger.info("[PYTHON] Interpréteur trouvé : " + cmd);
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        throw new RuntimeException("Aucun interpréteur Python trouvé dans le PATH.");
    }

    @Transactional(readOnly = true)
    public List<MockProject> getProjectsByCurrentUser() {
        User currentUser = getCurrentUser();
        List<MockProject> projects = mockProjectRepository.findByUser(currentUser);
        logger.info("[INFO] Projets trouvés pour l'utilisateur " + currentUser.getUsername() + " : " + projects.size());
        return projects;
    }

    @Transactional
    public void deleteProject(MockProject project) throws IOException {
        Path projectDir = Paths.get(project.getPath());
        if (Files.exists(projectDir)) {
            deleteDirectoryRecursively(projectDir.toFile());
            logger.info("[DELETE] Dossier supprimé : " + projectDir);
        }
        mockProjectRepository.delete(project);
        logger.info("[DELETE] Projet supprimé de la base : " + project.getName());
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

    private MockProject findProjectByName(String projectName) {
        User currentUser = getCurrentUser();
        return mockProjectRepository.findByUser(currentUser)
                .stream()
                .filter(p -> p.getName().equals(projectName))
                .findFirst()
                .orElse(null);
    }

    public void startDockerContainer(String projectName) throws IOException, InterruptedException {
        runDockerScript("start", projectName);
    }

    public void stopDockerContainer(String projectName) throws IOException, InterruptedException {
        runDockerScript("stop", projectName);
    }

    private void runDockerScript(String action, String projectName) throws IOException, InterruptedException {
        // Chemin du dossier script_py global
        Path scriptPyDir = globalScriptPyDir;
        if (!Files.exists(scriptPyDir) || !Files.isDirectory(scriptPyDir)) {
            throw new IllegalStateException("Dossier script_py introuvable : " + scriptPyDir);
        }

        String pythonCommand = detectPython();

        java.util.List<String> command = new java.util.ArrayList<>();
        command.add(pythonCommand);
        command.add("docker_control.py");
        command.add(action);
        command.add(projectName);

        logger.info("[DOCKER] Exécution : " + String.join(" ", command) + " dans " + scriptPyDir);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(scriptPyDir.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logger.info("[DOCKER][OUT] " + line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Échec du script Docker (" + action + "), code=" + exitCode);
        }
    }
}