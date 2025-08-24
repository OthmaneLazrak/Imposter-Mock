import os
from pathlib import Path
import typer
import subprocess

app = typer.Typer()

def get_base_dir() -> Path:
    base_env = os.getenv("BASE_DIR")
    if base_env:
        return Path(base_env)
    else:
        return Path(__file__).parent.parent / "projets_mocks"

BASE_DIR = get_base_dir()

@app.command()
def start(project: str):
    project_path = BASE_DIR / project

    if not project_path.exists():
        typer.echo(f"Le dossier {project_path} n'existe pas", err=True)
        raise typer.Exit(code=1)

    container_name = f"mock-{project}"

    # Sur Windows, convertit en chemin Unix pour Docker si besoin
    project_path_str = str(project_path)
    if os.name == 'nt':
        # Remplacer backslash par slash
        project_path_str = project_path_str.replace("\\", "/")

    cmd = [
        "docker", "run", "-d",
        "--name", container_name,
        "-p", "8080:8080",
        "-v", f"{project_path_str}:/opt/imposter/config",
        "outofcoffee/imposter"
    ]

    try:
        subprocess.run(cmd, check=True)
        typer.echo(f"Conteneur {container_name} lancé avec succès.")
    except subprocess.CalledProcessError as e:
        typer.echo(f"Erreur de lancement du conteneur : {e}", err=True)
        raise typer.Exit(code=1)

@app.command()
def stop(project: str):
    container_name = f"mock-{project}"

    try:
        subprocess.run(["docker", "stop", container_name], check=True)
        subprocess.run(["docker", "rm", container_name], check=True)
        typer.echo(f"Conteneur {container_name} arrêté et supprimé.")
    except subprocess.CalledProcessError as e:
        typer.echo(f"Erreur lors de l'arrêt du conteneur : {e}", err=True)
        raise typer.Exit(code=1)

if __name__ == "__main__":
    app()
