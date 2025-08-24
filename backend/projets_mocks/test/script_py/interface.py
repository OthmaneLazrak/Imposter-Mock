import tkinter as tk
from tkinter import filedialog, messagebox, ttk
import subprocess
from pathlib import Path
import threading
import os
from datetime import datetime

class MockManagerApp:
    def __init__(self, root):
        self.root = root
        self.base_dir = Path("C:/Users/othmane/Desktop/projets_mocks")
        self.scripts_dir = Path("C:/Users/othmane/Desktop/script_py")
        self.wsdl_path_var = tk.StringVar()
        self.xsd_path_var = tk.StringVar()  # Variable pour le fichier XSD optionnel
        self.project_name_var = tk.StringVar()
        self.project_info_var = tk.StringVar()
        self.status_var = tk.StringVar(value="Prêt")
        self.tags_configured = False
        self.setup_ui()
        
    def setup_ui(self):
        self.root.title("Imposter Mock Manager")
        self.root.geometry("800x600")
        self.root.configure(bg='#f0f0f0')
        
        style = ttk.Style()
        style.theme_use('clam')
        
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        self.root.columnconfigure(0, weight=1)
        self.root.rowconfigure(0, weight=1)
        main_frame.columnconfigure(0, weight=1)
        main_frame.rowconfigure(1, weight=1)
        
        # Titre principal
        title_label = ttk.Label(main_frame, text="Imposter Mock Manager", 
                            font=('Arial', 16, 'bold'))
        title_label.grid(row=0, column=0, pady=(0, 20))
        
        # Création du notebook (onglets)
        self.notebook = ttk.Notebook(main_frame)
        self.notebook.grid(row=1, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        # Onglet 1: Configuration
        self.config_frame = ttk.Frame(self.notebook, padding="10")
        self.notebook.add(self.config_frame, text="Configuration")
        
        # Onglet 2: Projets & Sortie
        self.projects_frame = ttk.Frame(self.notebook, padding="10")
        self.notebook.add(self.projects_frame, text="Projets")
        
        # Configuration de l'onglet Configuration
        self.setup_config_tab()
        
        # Configuration de l'onglet Projets & Sortie
        self.setup_projects_tab()
        
        # Barre de statut
        self.create_status_bar()

    def setup_config_tab(self):
        """Configure l'onglet de configuration"""
        self.config_frame.columnconfigure(0, weight=1)
        self.config_frame.rowconfigure(3, weight=1)
        
        # Configuration WSDL
        self.wsdl_frame = ttk.LabelFrame(self.config_frame, text="Configuration WSDL", padding="10")
        self.wsdl_frame.grid(row=0, column=0, sticky=(tk.W, tk.E), pady=(0, 10))
        self.wsdl_frame.columnconfigure(1, weight=1)
        
        ttk.Label(self.wsdl_frame, text="Fichier WSDL :").grid(row=0, column=0, sticky="w", padx=(0, 5))
        self.wsdl_entry = ttk.Entry(self.wsdl_frame, textvariable=self.wsdl_path_var, width=50)
        self.wsdl_entry.grid(row=0, column=1, sticky=(tk.W, tk.E), padx=(0, 5))
        ttk.Button(self.wsdl_frame, text="Parcourir...", command=self.browse_wsdl).grid(row=0, column=2)
        
        ttk.Label(self.wsdl_frame, text="Fichier XSD (optionnel) :").grid(row=1, column=0, sticky="w", padx=(0, 5), pady=(5,0))
        self.xsd_entry = ttk.Entry(self.wsdl_frame, textvariable=self.xsd_path_var, width=50)
        self.xsd_entry.grid(row=1, column=1, sticky=(tk.W, tk.E), padx=(0, 5), pady=(5,0))
        ttk.Button(self.wsdl_frame, text="Parcourir...", command=self.browse_xsd).grid(row=1, column=2, pady=(5,0))
        
        # Projet
        project_frame = ttk.LabelFrame(self.config_frame, text="Projet", padding="10")
        project_frame.grid(row=1, column=0, sticky=(tk.W, tk.E), pady=(0, 10))
        project_frame.columnconfigure(1, weight=1)
        
        ttk.Label(project_frame, text="Nom du projet :").grid(row=0, column=0, sticky="w", padx=(0, 5))
        self.project_entry = ttk.Entry(project_frame, textvariable=self.project_name_var, width=30)
        self.project_entry.grid(row=0, column=1, sticky=(tk.W, tk.E), padx=(0, 5))
        
        self.project_info_label = ttk.Label(project_frame, textvariable=self.project_info_var, 
                                        foreground='gray', font=('Arial', 8))
        self.project_info_label.grid(row=1, column=0, columnspan=2, sticky="w", pady=(5, 0))
        
        self.project_name_var.trace_add('write', self.update_project_info)
        
        # Actions
        actions_frame = ttk.LabelFrame(self.config_frame, text="Actions", padding="10")
        actions_frame.grid(row=2, column=0, sticky=(tk.W, tk.E), pady=(0, 10))
        
        self.progress_var = tk.DoubleVar()
        self.progress_bar = ttk.Progressbar(actions_frame, variable=self.progress_var, 
                                        mode='indeterminate')
        self.progress_bar.grid(row=0, column=0, columnspan=3, sticky=(tk.W, tk.E), pady=(0, 10))
        
        
        self.generate_btn = ttk.Button(actions_frame, text="⚡ Générer", 
                                    command=self.run_generate_threaded, width=20)
        self.generate_btn.grid(row=1, column=1, padx=5)
        
        self.validate_btn = ttk.Button(actions_frame, text="✅ Valider", 
                                    command=self.run_validate_threaded, width=20)
        self.validate_btn.grid(row=1, column=2, padx=(5, 0))
        
        self.status_label = ttk.Label(actions_frame, textvariable=self.status_var, 
                                    foreground='green', font=('Arial', 9))
        self.status_label.grid(row=2, column=0, columnspan=3, pady=(10, 0))

    def setup_projects_tab(self):
        """Configure l'onglet des projets et de la sortie"""
        self.projects_frame.columnconfigure(0, weight=1)
        self.projects_frame.rowconfigure(1, weight=1)
        
        # Projets existants
        self.existing_frame = ttk.LabelFrame(self.projects_frame, text="Projets existants", padding="10")
        self.existing_frame.grid(row=0, column=0, sticky=(tk.W, tk.E), pady=(0, 10))
        self.existing_frame.columnconfigure(0, weight=1)
        
        # Actions Docker pour les projets
        docker_actions_frame = ttk.Frame(self.existing_frame)
        docker_actions_frame.grid(row=0, column=0, sticky=(tk.W, tk.E), pady=(0, 10))
        
        self.launch_docker_btn = ttk.Button(docker_actions_frame, text="🚀 Lancer Docker",
                                    command=self.run_docker_start, width=20)
        self.launch_docker_btn.grid(row=0, column=0, padx=(0, 5))

        self.stop_docker_btn = ttk.Button(docker_actions_frame, text="🛑 Stopper Docker",
                                command=self.run_docker_stop, width=20)
        self.stop_docker_btn.grid(row=0, column=1, padx=(5, 0))
        
        # Liste des projets existants (sera remplie par refresh_existing_projects)
        self.refresh_existing_projects()
        
        # Sortie
        output_frame = ttk.LabelFrame(self.projects_frame, text="Sortie", padding="10")
        output_frame.grid(row=1, column=0, sticky=(tk.W, tk.E, tk.N, tk.S), pady=(0, 10))
        output_frame.columnconfigure(0, weight=1)
        output_frame.rowconfigure(0, weight=1)
        
        text_frame = ttk.Frame(output_frame)
        text_frame.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        text_frame.columnconfigure(0, weight=1)
        text_frame.rowconfigure(0, weight=1)
        
        self.output_text = tk.Text(text_frame, height=15, width=80, wrap=tk.WORD,
                                font=('Consolas', 9), bg='#1e1e1e', fg='#ffffff',
                                insertbackground='white')
        self.output_text.grid(row=0, column=0, sticky=(tk.W, tk.E, tk.N, tk.S))
        
        scrollbar = ttk.Scrollbar(text_frame, orient="vertical", command=self.output_text.yview)
        scrollbar.grid(row=0, column=1, sticky=(tk.N, tk.S))
        self.output_text.configure(yscrollcommand=scrollbar.set)
        
        ttk.Button(output_frame, text="Effacer", command=self.clear_output).grid(row=1, column=0, pady=(10, 0))
        
        # Binding pour la touche Entrée
        self.root.bind('<Return>', lambda e: self.run_generate_threaded())
        
    def create_status_bar(self):
        status_frame = ttk.Frame(self.root)
        status_frame.grid(row=1, column=0, sticky=(tk.W, tk.E))
        
        self.status_bar_var = tk.StringVar(value=f"Répertoire de base: {self.base_dir} | Scripts: {self.scripts_dir}")
        ttk.Label(status_frame, textvariable=self.status_bar_var, 
                 relief=tk.SUNKEN, anchor=tk.W).grid(row=0, column=0, sticky=(tk.W, tk.E))
        status_frame.columnconfigure(0, weight=1)
        
    def update_project_info(self, *args):
        project_name = self.project_name_var.get()
        if project_name:
            project_path = self.base_dir / project_name
            self.project_info_var.set(f"Chemin: {project_path}")
        else:
            self.project_info_var.set("")
            
    def browse_wsdl(self):
        filename = filedialog.askopenfilename(
            title="Sélectionner un fichier WSDL",
            filetypes=[("Fichiers WSDL", "*.wsdl"), ("Tous les fichiers", "*.*")]
        )
        if filename:
            self.wsdl_path_var.set(filename)
            
    def browse_xsd(self):
        filename = filedialog.askopenfilename(
            title="Sélectionner un fichier XSD",
            filetypes=[("Fichiers XSD", "*.xsd"), ("Tous les fichiers", "*.*")]
        )
        if filename:
            self.xsd_path_var.set(filename)

    def log_message(self, message, level="INFO"):
        timestamp = datetime.now().strftime("%H:%M:%S")
        color_tag = f"{level.lower()}_tag"
        
        if not self.tags_configured:
            self.output_text.tag_configure("info_tag", foreground="#00ff00")
            self.output_text.tag_configure("error_tag", foreground="#ff4444")
            self.output_text.tag_configure("warning_tag", foreground="#ffaa00")
            self.output_text.tag_configure("timestamp_tag", foreground="#888888")
            self.tags_configured = True
        
        self.output_text.insert(tk.END, f"[{timestamp}] ", "timestamp_tag")
        self.output_text.insert(tk.END, f"{level}: {message}\n", color_tag)
        self.output_text.see(tk.END)
        self.root.update_idletasks()
        print(message)
        
    def clear_output(self):
        self.output_text.delete(1.0, tk.END)
        
    def set_buttons_state(self, state):
        self.generate_btn.configure(state=state)
        self.validate_btn.configure(state=state)
        
    def run_command(self, cmd, operation_name):
        try:
            self.set_buttons_state('disabled')
            self.progress_bar.start()
            self.status_var.set(f"Exécution de {operation_name}...")
            
            self.log_message(f"Démarrage de {operation_name}")
            self.log_message(f"Commande: {' '.join(cmd)}")
            
            original_dir = os.getcwd()
            os.chdir(self.scripts_dir)
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
            
            os.chdir(original_dir)
            
            if result.stdout:
                self.log_message(result.stdout.strip())
            if result.stderr:
                self.log_message(result.stderr.strip(), "ERROR")
                
            if result.returncode == 0:
                self.log_message(f"{operation_name} terminé avec succès")
                self.status_var.set(f"{operation_name} terminé avec succès")
            else:
                self.log_message(f"{operation_name} échoué (code: {result.returncode})", "ERROR")
                self.status_var.set(f"{operation_name} échoué")
                
        except subprocess.TimeoutExpired:
            self.log_message(f"Timeout: {operation_name} a pris trop de temps", "ERROR")
            self.status_var.set(f"Timeout: {operation_name}")
        except Exception as e:
            self.log_message(f"Erreur lors de {operation_name}: {str(e)}", "ERROR")
            self.status_var.set(f"Erreur: {operation_name}")
        finally:
            self.progress_bar.stop()
            self.set_buttons_state('normal')
            
    def validate_inputs(self, require_wsdl=True):
        wsdl = self.wsdl_path_var.get().strip()
        xsd = self.xsd_path_var.get().strip()
        project = self.project_name_var.get().strip()
        
        if require_wsdl and not wsdl:
            messagebox.showerror("Erreur", "Le chemin du fichier WSDL est obligatoire.")
            return False
            
        if not project:
            messagebox.showerror("Erreur", "Le nom du projet est obligatoire.")
            return False
            
        if require_wsdl and not Path(wsdl).exists():
            messagebox.showerror("Erreur", f"Le fichier WSDL '{wsdl}' n'existe pas.")
            return False
            
        if xsd and not Path(xsd).exists():
            messagebox.showerror("Erreur", f"Le fichier XSD '{xsd}' n'existe pas.")
            return False
            
        if not self.scripts_dir.exists():
            messagebox.showerror("Erreur", f"Le dossier scripts '{self.scripts_dir}' n'existe pas.")
            return False
            
        return True
        
        
    def run_generate_threaded(self):
        if not self.validate_inputs(require_wsdl=False):
            return
            
        def run_generate():
            project = self.project_name_var.get()
            cmd = ["python", "generate.py", f"--project={project}"]
            # Si le projet n'existe pas, il faudra fournir aussi --wsdl et --xsd, par exemple :
            if not (self.base_dir / project).exists():
                wsdl = self.wsdl_path_var.get()
                if wsdl:
                    cmd.append(f"--wsdl={wsdl}")
                xsd = self.xsd_path_var.get()
                if xsd:
                    cmd.append(f"--xsd={xsd}")

            self.run_command(cmd, "Génération")
            self.refresh_existing_projects()

            
        threading.Thread(target=run_generate, daemon=True).start()
        
    def run_validate_threaded(self):
        if not self.validate_inputs(require_wsdl=False):
            return
            
        def run_validate():
            project = self.project_name_var.get()
            cmd = ["python", "validate.py", f"--project={self.base_dir / project}"]
            self.run_command(cmd, "Validation")
            self.refresh_existing_projects()

            
        threading.Thread(target=run_validate, daemon=True).start()
    
    def run_docker_start(self):
        if not self.validate_inputs(require_wsdl=False):
            return

        def start_container():
            project = self.project_name_var.get()
            cmd = ["python", "docker_control.py", "start", project]
            self.run_command(cmd, "Lancement Docker")

        threading.Thread(target=start_container, daemon=True).start()

    def run_docker_stop(self):
        if not self.validate_inputs(require_wsdl=False):
            return

        def stop_container():
            project = self.project_name_var.get()
            cmd = ["python", "docker_control.py", "stop", project]
            self.run_command(cmd, "Arrêt Docker")

        threading.Thread(target=stop_container, daemon=True).start()

    def refresh_existing_projects(self):
        for widget in self.existing_frame.winfo_children():
            widget.destroy()

        projects = sorted(p.name for p in self.base_dir.iterdir() if p.is_dir())

        for idx, project in enumerate(projects):
            label = ttk.Label(self.existing_frame, text=project)
            label.grid(row=idx, column=0, sticky="w")

            launch_btn = ttk.Button(
                self.existing_frame, text="Lancer",
                command=lambda p=project: self.launch_container(p)
           )
            launch_btn.grid(row=idx, column=1, padx=5)

            stop_btn = ttk.Button(
                self.existing_frame, text="Arrêter",
                command=lambda p=project: self.stop_container(p)
            )
            stop_btn.grid(row=idx, column=2, padx=5)

    def launch_container(self, project):
        cmd = ["python", "docker_control.py", "start", project]
        self.run_command(cmd, f"Lancement Docker ({project})")
        self.refresh_existing_projects()

    def stop_container(self, project):
        cmd = ["python", "docker_control.py", "stop", project]
        self.run_command(cmd, f"Arrêt Docker ({project})")
        self.refresh_existing_projects()




def main():
    root = tk.Tk()
    app = MockManagerApp(root)
    root.mainloop()

if __name__ == "__main__":
    main()
