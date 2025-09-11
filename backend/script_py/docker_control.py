import os
import subprocess
import sys
import yaml
import time

def get_config_dir():
    """Détermine le répertoire de configuration de manière dynamique"""
    # Option 1: Variable d'environnement pour le chemin de l'hôte (Docker-in-Docker)
    if "HOST_PROJECTS_DIR" in os.environ:
        host_projects_dir = os.environ["HOST_PROJECTS_DIR"]
        print(f"📁 Utilisation de HOST_PROJECTS_DIR pour Docker: {host_projects_dir}")
        return host_projects_dir

    # Option 2: Variable d'environnement
    if "PROJECTS_DIR" in os.environ:
        config_dir = os.environ["PROJECTS_DIR"]
        print(f"📁 Utilisation de PROJECTS_DIR: {config_dir}")
        return config_dir

    # Option 3: Argument de ligne de commande --config-dir
    for arg in sys.argv:
        if arg.startswith("--config-dir="):
            config_dir = arg.split("=", 1)[1]
            print(f"📁 Utilisation du chemin fourni: {config_dir}")
            return config_dir

    # Option 4: Recherche automatique du dossier "projects" dans les répertoires parents
    search_dir = os.getcwd()
    for _ in range(10):  # Augmenté à 10 niveaux
        projects_path = os.path.join(search_dir, "projects")
        if os.path.isdir(projects_path):
            print(f"📁 Dossier 'projects' trouvé automatiquement: {projects_path}")
            return projects_path

        parent = os.path.dirname(search_dir)
        if parent == search_dir:  # Racine atteinte
            break
        search_dir = parent

    # Option 5: Recherche dans les chemins communs
    common_paths = [
        "/projects",
        "/home/othmane/Bureau/mockImposter/projects",
        os.path.expanduser("~/Bureau/mockImposter/projects"),
        os.path.expanduser("~/mockimposter/projects")
    ]

    for path in common_paths:
        if os.path.exists(path):
            print(f"📁 Utilisation du chemin trouvé: {path}")
            return path

    # Option 6: Création du répertoire par défaut
    default_dir = os.path.expanduser("~/mockimposter/projects")
    print(f"📁 Création du répertoire par défaut: {default_dir}")
    os.makedirs(default_dir, exist_ok=True)
    return default_dir


def get_host_config_dir():
    """Retourne le chemin sur l'hôte pour le montage Docker (Docker-in-Docker)"""
    if "HOST_PROJECTS_DIR" in os.environ:
        return os.environ["HOST_PROJECTS_DIR"]

    # Fallback vers le répertoire de configuration normal
    return get_config_dir()


CONFIG_DIR = get_config_dir()


def validate_config_content(config_file):
    """Valide que le fichier YAML contient au moins un document avec `plugin`"""
    try:
        with open(config_file, "r", encoding="utf-8") as f:
            documents = list(yaml.safe_load_all(f))

        if not documents:
            print(f"❌ {config_file} est vide ou invalide")
            return False

        valid = any(isinstance(doc, dict) and "plugin" in doc for doc in documents)
        if valid:
            for doc in documents:
                if isinstance(doc, dict) and "plugin" in doc:
                    print(f"✅ Configuration valide : {os.path.basename(config_file)} (plugin: {doc['plugin']})")
        else:
            print(f"❌ Aucun `plugin` trouvé dans {config_file}")

        return valid

    except Exception as e:
        print(f"❌ Erreur lors de la validation de {config_file} : {e}")
        return False


def validate_project(project_name):
    """Vérifie qu'un projet contient une configuration Imposter valide"""
    # Utilise le chemin interne au conteneur pour la validation
    internal_config_dir = get_config_dir() if "HOST_PROJECTS_DIR" not in os.environ else os.environ.get("BASE_DIR", "/projects")

    project_path = os.path.join(internal_config_dir, project_name)
    config_file = os.path.join(project_path, "imposter-config.yaml")

    print(f"🔍 Vérification du projet: {project_path}")

    if not os.path.isdir(project_path):
        print(f"❌ Projet {project_name} introuvable dans {internal_config_dir}")
        try:
            available = [d for d in os.listdir(internal_config_dir) if os.path.isdir(os.path.join(internal_config_dir, d))]
            if available:
                print(f"📋 Projets disponibles: {', '.join(available)}")
        except Exception as e:
            print(f"❌ Erreur lors de la lecture du répertoire {internal_config_dir}: {e}")
        return False

    if not os.path.isfile(config_file):
        print(f"❌ Aucun fichier imposter-config.yaml trouvé dans {project_name}")
        try:
            files = os.listdir(project_path)
            print(f"📋 Fichiers trouvés: {', '.join(files)}")
        except Exception as e:
            print(f"❌ Erreur lors de la lecture du projet {project_path}: {e}")
        return False

    return validate_config_content(config_file)


def network_exists(network_name):
    """Vérifie si un réseau Docker existe"""
    try:
        result = subprocess.run(
            ["docker", "network", "ls", "--filter", f"name={network_name}", "--format", "{{.Name}}"],
            capture_output=True, text=True, check=False
        )
        return network_name in result.stdout.strip().split('\n')
    except Exception:
        return False


def run_command(cmd, check=True, capture_output=True):
    """Wrapper subprocess.run avec logs"""
    try:
        result = subprocess.run(cmd, check=check, capture_output=capture_output, text=True)
        return result
    except subprocess.CalledProcessError as e:
        print(f"❌ Erreur lors de l'exécution: {' '.join(cmd)}")
        if e.stderr:
            print(f"Détails: {e.stderr.strip()}")
        raise


def is_container_running(container_name):
    """Vérifie si un conteneur est en cours d'exécution"""
    try:
        result = run_command(
            ["docker", "ps", "-q", "-f", f"name={container_name}"],
            check=False
        )
        return bool(result.stdout.strip())
    except Exception:
        return False


def get_container_logs(container_name):
    """Récupère les logs d'un conteneur"""
    try:
        result = run_command(
            ["docker", "logs", container_name],
            check=False
        )
        return result.stdout + result.stderr
    except Exception as e:
        return f"Erreur lors de la récupération des logs: {e}"


def start_container(project_name, port, network="mocknet"):
    """Lance un conteneur Imposter pour un projet donné"""
    container_name = f"mock-{project_name}"

    # Utilise le chemin HOST pour le montage Docker
    host_config_dir = get_host_config_dir()
    host_project_path = os.path.join(host_config_dir, project_name)

    # Utilise le chemin interne pour la validation
    internal_config_dir = os.environ.get("BASE_DIR", "/projects") if "HOST_PROJECTS_DIR" in os.environ else get_config_dir()

    print(f"🚀 Tentative de démarrage du conteneur {container_name}")
    print(f"📁 Répertoire de configuration (hôte): {host_config_dir}")
    print(f"📁 Répertoire de configuration (interne): {internal_config_dir}")
    print(f"📂 Chemin du projet (hôte): {host_project_path}")

    if not validate_project(project_name):
        print(f"❌ Impossible de démarrer {container_name}, configuration invalide")
        return False

    # Stop + remove si existant
    print(f"🧹 Nettoyage des conteneurs existants...")
    run_command(["docker", "stop", container_name], check=False)
    run_command(["docker", "rm", container_name], check=False)

    # Construction de la commande avec le chemin HOST
    cmd = [
        "docker", "run", "-d",
        "--name", container_name,
        "-p", f"{port}:8080",
        "-v", f"{host_project_path}:/opt/imposter/config"
    ]

    # Ajouter le réseau seulement s'il est spécifié et existe
    if network:
        if network_exists(network):
            cmd.extend(["--network", network])
            print(f"🔗 Utilisation du réseau: {network}")
        else:
            print(f"⚠️ Le réseau '{network}' n'existe pas, utilisation du réseau par défaut")

    cmd.append("outofcoffee/imposter:latest")

    print(f"🔗 Montage: {host_project_path} -> /opt/imposter/config")
    print(f"🔧 Commande: {' '.join(cmd)}")

    try:
        result = run_command(cmd)
        container_id = result.stdout.strip()
        if container_id:
            print(f"✅ Conteneur {container_name} démarré (ID={container_id})")
        else:
            print(f"❌ Échec du démarrage du conteneur")
            return False

        # Attendre un peu que le conteneur démarre
        print(f"⏳ Attente de démarrage du conteneur...")
        time.sleep(5)

        # Vérifier l'état du conteneur
        if is_container_running(container_name):
            status = run_command(
                ["docker", "ps", "-f", f"name={container_name}", "--format", "{{.Status}}"],
                check=False
            ).stdout.strip()
            print(f"📊 Statut: {status}")
            print(f"✅ Conteneur actif")
            print(f"🌐 Test de connectivité sur http://localhost:{port}")
            return True
        else:
            print(f"❌ Le conteneur s'est arrêté après le démarrage. Logs:")
            logs = get_container_logs(container_name)
            print(logs)
            return False

    except Exception as e:
        print(f"❌ Erreur lors du démarrage: {e}")
        return False


def stop_container(project_name):
    """Arrête et supprime un conteneur"""
    container_name = f"mock-{project_name}"
    print(f"🛑 Arrêt du conteneur {container_name}...")

    run_command(["docker", "stop", container_name], check=False)
    run_command(["docker", "rm", container_name], check=False)
    print(f"✅ Conteneur {container_name} arrêté et supprimé")


def main():
    if len(sys.argv) < 3:
        print("Usage: python docker_control.py [start|stop] <project_name> [--port=PORT] [--config-dir=PATH] [--network=NETWORK]")
        print("\nOptions disponibles:")
        print("  --config-dir=PATH    : Chemin du répertoire des projets")
        print("  --network=NETWORK    : Réseau Docker à utiliser (optionnel)")
        print("  PROJECTS_DIR=PATH    : Variable d'environnement pour le répertoire des projets")
        print("  HOST_PROJECTS_DIR=PATH : Variable d'environnement pour le répertoire des projets sur l'hôte (Docker-in-Docker)")
        print("  Recherche automatique: remonte jusqu'à 10 niveaux pour trouver 'projects'")
        print(f"\nRépertoire détecté: {CONFIG_DIR}")

        # Afficher les projets disponibles
        try:
            if os.path.exists(CONFIG_DIR):
                available = [d for d in os.listdir(CONFIG_DIR) if os.path.isdir(os.path.join(CONFIG_DIR, d))]
                if available:
                    print(f"📋 Projets disponibles: {', '.join(available)}")
        except Exception:
            pass

        sys.exit(1)

    action = sys.argv[1]
    project_name = sys.argv[2]
    port = 8080
    network = None

    for arg in sys.argv[3:]:
        if arg.startswith("--port="):
            port = int(arg.split("=")[1])
        elif arg.startswith("--network="):
            network = arg.split("=")[1]

    print(f"🎯 Action: {action}")
    print(f"📦 Projet: {project_name}")
    print(f"🚪 Port: {port}")

    if action == "start":
        success = start_container(project_name, port, network)
        sys.exit(0 if success else 1)
    elif action == "stop":
        stop_container(project_name)
    else:
        print("❌ Action inconnue. Utilisez start ou stop")
        sys.exit(1)


if __name__ == "__main__":
    main()