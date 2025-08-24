import typer
import yaml
from pathlib import Path

app = typer.Typer()

BASE_DIR = Path("C:/Users/othmane/Desktop/projets_mocks")

@app.command()
def validate(
    project: str = typer.Option(..., help="Nom du projet dans projets_mocks")
):
    """
    Valide la structure de config.yaml + vérifie l'existence du WSDL.
    """

    project_path = BASE_DIR / project
    config_path = project_path / "imposter-config.yaml"

    if not config_path.exists():
        typer.echo("❌ Fichier config.yaml introuvable.")
        raise typer.Exit(code=1)

    # Chargement du YAML
    try:
        with open(config_path, "r") as f:
            config = yaml.safe_load(f)
    except yaml.YAMLError as e:
        typer.echo(f"❌ Erreur YAML : {e}")
        raise typer.Exit(code=1)

    if not isinstance(config, dict):
        typer.echo("❌ config.yaml doit contenir un dictionnaire.")
        raise typer.Exit(code=1)

    # Champs obligatoires
    required_fields = ["plugin", "wsdlFile", "resources"]
    for field in required_fields:
        if field not in config:
            typer.echo(f"❌ Champ manquant : {field}")
            raise typer.Exit(code=1)

    # Vérification du WSDL
    wsdl_filename = config["wsdlFile"]
    wsdl_path = project_path / wsdl_filename
    if not wsdl_path.exists():
        typer.echo(f"❌ Fichier WSDL non trouvé dans le dossier : {wsdl_filename}")
        raise typer.Exit(code=1)
    else:
        typer.echo(f"✅ WSDL trouvé : {wsdl_filename}")

    # Vérification des resources
    if not isinstance(config["resources"], list):
        typer.echo("❌ 'resources' doit être une liste.")
        raise typer.Exit(code=1)

    for i, resource in enumerate(config["resources"], 1):
        if "path" not in resource or "response" not in resource:
            typer.echo(f"❌ Ressource #{i} invalide (path/response manquant).")
            raise typer.Exit(code=1)

        script_file = resource["response"].get("scriptFile")
        if script_file:
            script_path = project_path / script_file
            if not script_path.exists():
                typer.echo(f"❌ scriptFile manquant : {script_file}")
                raise typer.Exit(code=1)
            else:
                typer.echo(f"✅ scriptFile trouvé : {script_file}")

    typer.echo("✅ config.yaml est structurellement VALIDE ✅")

if __name__ == "__main__":
    app()
