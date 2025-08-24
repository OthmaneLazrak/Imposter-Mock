import os
from pathlib import Path
import typer
import yaml
from lxml import etree
from urllib.parse import urlparse
import shutil

app = typer.Typer()

def get_base_dir() -> Path:
    base_env = os.getenv("BASE_DIR")
    if base_env:
        return Path(base_env)
    else:
        # Par défaut, chemin relatif au dossier du script
        return Path(__file__).parent.parent / "projets_mocks"

BASE_DIR = get_base_dir()

def init_project(project_path: Path, wsdl_path: Path, xsd_path: Path | None = None):
    project_path.mkdir(parents=True, exist_ok=True)
    wsdl_dest = project_path / wsdl_path.name
    shutil.copy2(wsdl_path, wsdl_dest)
    if xsd_path and xsd_path.exists():
        xsd_dir = project_path / "xsd"
        xsd_dir.mkdir(exist_ok=True)
        xsd_dest = xsd_dir / xsd_path.name
        shutil.copy2(xsd_path, xsd_dest)
        typer.echo(f"📄 Fichier XSD copié dans : {xsd_dest}")
    (project_path / "imposter-config.yaml").write_text("")
    groovy_code = """\
/*
 Script Groovy basique pour Imposter
 Renvoie le corps reçu tel quel.
*/
return [body: request.body]
"""
    (project_path / "response.groovy").write_text(groovy_code)
    typer.echo(f"✅ Projet mock initialisé dans {project_path}")
    typer.echo(f"📄 Fichier WSDL copié : {wsdl_path.name}")
    typer.echo("📝 config.yaml vide et response.groovy généré.")

@app.command()
def generate(
    project: str = typer.Option(..., help="Nom du dossier du projet dans projets_mocks"),
    wsdl: str = typer.Option(None, help="Chemin vers le fichier WSDL (requis si le projet n'existe pas)"),
    xsd: str = typer.Option(None, help="Chemin vers le fichier XSD optionnel")
):
    project_path = BASE_DIR / project

    # Si dossier projet n'existe pas, init automatique
    if not project_path.exists():
        if not wsdl:
            typer.echo("❌ Le projet n'existe pas. Vous devez fournir le paramètre --wsdl pour l'initialiser.")
            raise typer.Exit(code=1)

        wsdl_path = Path(wsdl)
        if not wsdl_path.exists():
            typer.echo(f"❌ Fichier WSDL introuvable : {wsdl_path}")
            raise typer.Exit(code=1)

        xsd_path = Path(xsd) if xsd else None

        typer.echo(f"🚀 Initialisation du projet '{project}' ...")
        init_project(project_path, wsdl_path, xsd_path)

    # Maintenant on peut générer config.yaml

    # Recherche fichier .wsdl dans le dossier (devrait exister)
    wsdl_files = list(project_path.glob("*.wsdl"))
    if not wsdl_files:
        typer.echo("❌ Aucun fichier WSDL (.wsdl) trouvé dans le dossier.")
        raise typer.Exit(code=1)
    if len(wsdl_files) > 1:
        typer.echo("❌ Plusieurs fichiers .wsdl trouvés. Merci d’en laisser un seul.")
        for f in wsdl_files:
            typer.echo(f" - {f.name}")
        raise typer.Exit(code=1)

    wsdl_path = wsdl_files[0]
    typer.echo(f"📄 WSDL trouvé : {wsdl_path.name}")

    # Analyse WSDL
    tree = etree.parse(str(wsdl_path))
    root = tree.getroot()
    nsmap = {
        'wsdl': 'http://schemas.xmlsoap.org/wsdl/',
        'soap': 'http://schemas.xmlsoap.org/wsdl/soap/'
    }

    soap_address = root.find(".//soap:address", namespaces=nsmap)
    if soap_address is None:
        typer.echo("❌ <soap:address> introuvable dans le WSDL.")
        raise typer.Exit(code=1)

    location_url = soap_address.attrib.get("location")
    if not location_url:
        typer.echo("❌ L’attribut 'location' est vide.")
        raise typer.Exit(code=1)

    path = urlparse(location_url).path or "/defaultPath"
    typer.echo(f"🔍 Chemin SOAP extrait : {path}")

    # Génération config.yaml
    config = {
        "plugin": "soap",
        "wsdlFile": wsdl_path.name,
        "resources": [
            {
                "path": path,
                "response": {
                    "scriptFile": "response.groovy"
                }
            }
        ]
    }

    config_path = project_path / "imposter-config.yaml"
    with open(config_path, "w") as f:
        yaml.dump(config, f, sort_keys=False)

    typer.echo(f"✅ config.yaml généré dans : {config_path}")

if __name__ == "__main__":
    app()
