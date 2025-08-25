import sys
import subprocess
from pathlib import Path
import platform
import os
import glob

def dockerize_path(path: Path) -> str:
    """Convertit le chemin pour qu'il soit compatible avec Docker sur Linux/Windows."""
    abs_path = str(path.absolute())
    if platform.system() == "Windows":
        # Exemple : C:\Users\othmane\Bureau -> /c/Users/othmane/Bureau
        abs_path = abs_path.replace("\\", "/")
        if abs_path[1] == ":":
            drive = abs_path[0].lower()
            abs_path = f"/{drive}{abs_path[2:]}"
    return abs_path

def check_config_files(project_path: Path) -> bool:
    """Vérifie si le dossier contient des fichiers de configuration Imposter valides."""
    # Extensions de fichiers supportées par Imposter
    config_extensions = ["*.json", "*.yaml", "*.yml"]

    config_files = []
    for ext in config_extensions:
        config_files.extend(glob.glob(str(project_path / ext)))

    if not config_files:
        print(f"⚠️  Aucun fichier de configuration trouvé dans {project_path}")
        print("   Fichiers attendus : *.json, *.yaml, *.yml")
        return False

    print(f"✅ Fichiers de configuration trouvés :")
    for config_file in config_files:
        print(f"   - {os.path.basename(config_file)}")

    return True

def list_directory_contents(project_path: Path):
    """Affiche le contenu du répertoire pour debugging."""
    if project_path.exists():
        print(f"📁 Contenu de {project_path}:")
        try:
            for item in project_path.iterdir():
                item_type = "📂" if item.is_dir() else "📄"
                print(f"   {item_type} {item.name}")
        except PermissionError:
            print("   ❌ Permissions insuffisantes pour lister le contenu")
    else:
        print(f"❌ Le répertoire {project_path} n'existe pas")

def check_docker_availability():
    """Vérifie si Docker CLI est disponible."""
    try:
        result = subprocess.run(["docker", "--version"], capture_output=True, text=True)
        if result.returncode == 0:
            print(f"✅ Docker disponible: {result.stdout.strip()}")
            return True
        else:
            print("❌ Docker CLI trouvé mais ne répond pas correctement")
            return False
    except FileNotFoundError:
        print("❌ Docker CLI non trouvé. Installez Docker dans le conteneur.")
        return False

def start_container(project_name: str):
    container_name = f"mock-{project_name}"

    # Vérifier que Docker est disponible
    if not check_docker_availability():
        print("💡 Solution: Ajoutez Docker CLI à votre Dockerfile du backend")
        return

    # Utiliser la variable d'environnement BASE_DIR ou le chemin par défaut dans le conteneur
    base_dir = os.getenv("BASE_DIR", "/projects")
    project_path = Path(f"{base_dir}/{project_name}")

    if not project_path.exists():
        print(f"❌ Le dossier {project_path} n'existe pas")
        return

    # Afficher le contenu du répertoire pour debugging
    list_directory_contents(project_path)

    # Vérifier la présence de fichiers de configuration
    if not check_config_files(project_path):
        print("❌ Impossible de démarrer le conteneur sans fichiers de configuration")
        print("💡 Assurez-vous que votre projet contient au moins un fichier de configuration Imposter (.json, .yaml, .yml)")
        return

    # IMPORTANT : Dans le conteneur, on monte /projects, mais Docker a besoin du chemin HOST
    # Le chemin host est récupéré via une variable d'environnement ou calculé
    host_base_dir = os.getenv("HOST_PROJECTS_DIR", "/home/othmane/Bureau/mockImposter/projects")
    docker_project_path = f"{host_base_dir}/{project_name}"

    # Vérifier si le conteneur existe déjà
    result = subprocess.run(
        ["docker", "ps", "-a", "--filter", f"name={container_name}", "--format", "{{.Status}}"],
        capture_output=True, text=True
    )
    status = result.stdout.strip()

    if status:
        if status.startswith("Exited"):
            print(f"⚠️ Le conteneur {container_name} existe déjà (status: exited). Redémarrage...")
            subprocess.run(["docker", "start", container_name])
        elif status.startswith("Up"):
            print(f"✅ Le conteneur {container_name} est déjà en cours d'exécution")
        else:
            print(f"ℹ️ Statut inconnu ({status}), tentative de démarrage...")
            subprocess.run(["docker", "start", container_name])
        return

    # Commande identique à celle que tu lances manuellement
    command = [
        "docker", "run", "-d",
        "--name", container_name,
        "-p", "8080:8080",
        "-v", f"{docker_project_path}:/opt/imposter/config",
        "outofcoffee/imposter"
    ]

    print(f"🚀 Lancement du conteneur {container_name}...")
    print(f"📂 Montage : {docker_project_path} -> /opt/imposter/config")

    result = subprocess.run(command, capture_output=True, text=True)

    if result.returncode == 0:
        print(f"✅ Conteneur {container_name} démarré avec succès")
        # Attendre un peu puis vérifier les logs
        print("🔍 Vérification des logs...")
        subprocess.run(["sleep", "3"])
        subprocess.run(["docker", "logs", container_name])
    else:
        print(f"❌ Erreur lors du démarrage : {result.stderr}")

def stop_container(project_name: str):
    container_name = f"mock-{project_name}"
    subprocess.run(["docker", "stop", container_name])
    subprocess.run(["docker", "rm", container_name])
    print(f"🛑 Conteneur {container_name} arrêté et supprimé.")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python docker_control.py [start|stop] <project_name>")
        print("")
        print("Pour diagnostiquer un projet :")
        print("  python docker_control.py start <project_name>")
        sys.exit(1)

    action = sys.argv[1]
    project_name = sys.argv[2]

    if action == "start":
        start_container(project_name)
    elif action == "stop":
        stop_container(project_name)
    else:
        print(f"❌ Action inconnue: {action}")
        print("Actions disponibles: start, stop")