import typer
from pathlib import Path
import shutil

app = typer.Typer()

BASE_DIR = Path("C:/Users/othmane/Desktop/projets_mocks")

@app.command()
def init(
    wsdl: str = typer.Option(..., help="Chemin vers le fichier WSDL à copier"),
    output_dir: str = typer.Option("mock-project", help="Nom du dossier à créer dans le dossier fixe"),
    xsd: str = typer.Option(None, help="Chemin vers un fichier XSD optionnel à copier dans un dossier 'xsd'")
):
    """
    Initialise un dossier de mock SOAP avec un config vide, un script Groovy standard, le WSDL fourni,
    et copie un fichier XSD optionnel dans un sous-dossier 'xsd'.
    """
    wsdl_path = Path(wsdl)
    if not wsdl_path.exists():
        typer.echo(f"❌ Fichier WSDL introuvable : {wsdl_path}")
        raise typer.Exit(code=1)

    project_path = BASE_DIR / output_dir
    project_path.mkdir(parents=True, exist_ok=True)

    # Copier le fichier WSDL dans le dossier principal du projet
    wsdl_dest = project_path / wsdl_path.name
    shutil.copy2(wsdl_path, wsdl_dest)

    # Si un fichier XSD est fourni, créer un dossier 'xsd' et copier le fichier dedans
    if xsd:
        xsd_path = Path(xsd)
        if xsd_path.exists():
            xsd_dir = project_path / "xsd"
            xsd_dir.mkdir(exist_ok=True)
            xsd_dest = xsd_dir / xsd_path.name
            shutil.copy2(xsd_path, xsd_dest)
            typer.echo(f"📄 Fichier XSD copié dans : {xsd_dest}")
        else:
            typer.echo(f"⚠️ Fichier XSD introuvable : {xsd_path}")

    # Créer un fichier config.yaml vide
    (project_path / "imposter-config.yaml").write_text("")

    # Créer un script response.groovy basique
    groovy_code = """\
/*
 Script Groovy basique pour Imposter
 Renvoie le corps reçu tel quel.
*/
return [body: request.body]
"""
    (project_path / "response.groovy").write_text(groovy_code)

    typer.echo(f"✅ Projet mock initialisé dans {project_path}")
    typer.echo(f"📄 Fichier WSDL copié : {wsdl_dest.name}")
    typer.echo("📝 config.yaml vide et response.groovy généré.")

if __name__ == "__main__":
    app()
