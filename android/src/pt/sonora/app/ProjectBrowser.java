package pt.sonora.app;

import android.app.*;
import android.graphics.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Named projects, old autosave recovery and document-only management. */
final class ProjectBrowser {
  final EditorController c;

  ProjectBrowser(EditorController controller) {
    c = controller;
  }

  void create() {
    if (c.busy) {
      c.message("Aguarda a operação atual.");
      return;
    }
    Project p = new Project();
    p.name = "Projeto " + (c.store.projects().length + 1);
    try {
      c.store.save(p, false);
      c.enterEditor(p, true);
    } catch (Exception e) {
      c.message(e.getMessage());
    }
  }

  void open(Project project) {
    if (c.busy) {
      c.message("Aguarda a operação atual.");
      return;
    }
    c.enterEditor(project, false);
  }

  void menu(Project project) {
    new AlertDialog.Builder(c)
        .setTitle(project.name)
        .setItems(
            new String[] {"Continuar edição", "Renomear", "Duplicar projeto", "Eliminar projeto"},
            (d, i) -> {
              switch (i) {
                case 0:
                  open(project);
                  break;
                case 1:
                  rename(project);
                  break;
                case 2:
                  duplicate(project);
                  break;
                case 3:
                  confirmDelete(project);
                  break;
              }
            })
        .setNegativeButton("Fechar", null)
        .show();
  }

  void rename(Project project) {
    LinearLayout box = c.fields();
    EditText input = c.input(box, "Nome do projeto", project.name);
    new AlertDialog.Builder(c)
        .setTitle("Renomear projeto")
        .setView(box)
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Guardar",
            (d, w) -> {
              try {
                String name = input.getText().toString().trim();
                if (name.isEmpty() || name.length() > 160)
                  throw new IOException("Usa um nome entre 1 e 160 caracteres.");
                Project next = project.copy();
                next.name = name;
                c.store.save(next, false);
                if (c.project.id.equals(next.id)) {
                  c.project.name = name;
                  c.store.save(c.project, true);
                }
                c.showHome();
              } catch (Exception e) {
                c.message(e.getMessage());
              }
            })
        .show();
  }

  Project duplicate(Project source) {
    try {
      Project p = ProjectActions.duplicate(source);
      c.store.save(p, false);
      c.showHome();
      return p;
    } catch (Exception e) {
      c.message(e.getMessage());
      return null;
    }
  }

  void confirmDelete(Project p) {
    new AlertDialog.Builder(c)
        .setTitle("Eliminar projeto?")
        .setMessage(
            p.name
                + "\n"
                + "Só o projeto e a sua miniatura serão removidos. Os vídeos, músicas e imagens"
                + " originais ficam preservados.")
        .setNegativeButton("Cancelar", null)
        .setPositiveButton(
            "Eliminar",
            (d, w) -> {
              try {
                delete(p);
                c.showHome();
              } catch (Exception e) {
                c.message(e.getMessage());
              }
            })
        .show();
  }

  void delete(Project p) throws Exception {
    Project.read(p.json().toString());
    new android.util.AtomicFile(new File(c.store.directory, p.id + ".json")).delete();
    new File(c.store.directory, p.id + ".jpg").delete();
    File auto = new File(c.store.directory, "autosave.json");
    if (auto.exists() && c.store.load(auto).id.equals(p.id))
      new android.util.AtomicFile(auto).delete();
    if (c.project.id.equals(p.id)) {
      c.project = new Project();
      c.editorVisible = false;
    }
  }

  void savePoster(Project p, Bitmap bitmap) {
    Bitmap copy = bitmap.copy(Bitmap.Config.ARGB_8888, false);
    String id = p.id;
    c.worker.execute(
        () -> {
          try (OutputStream out = new FileOutputStream(new File(c.store.directory, id + ".jpg"))) {
            copy.compress(Bitmap.CompressFormat.JPEG, 85, out);
          } catch (Exception ignored) {
          } finally {
            copy.recycle();
          }
        });
  }

  void settings() {
    LinearLayout body = UiTheme.column(c);
    UiTheme.item(
        body, UiTheme.primary(c, "Diagnóstico de transcrição", c::speechDiagnostics), -1, 48);
    UiTheme.item(
        body,
        UiTheme.primary(c, "Importar projeto JSON", () -> c.pick(13, "application/json", false)),
        -1,
        48);
    UiTheme.item(
        body,
        UiTheme.primary(
            c,
            "Modo compatível Sonora 0.3",
            () ->
                c.startActivity(
                    new android.content.Intent(c, pt.sonora.offline.MainActivity.class))),
        -1,
        48);
    UiTheme.item(
        body,
        UiTheme.text(
            c,
            "Sonora Lab · Fase 6\n"
                + "Processamento local · Android 8+\n"
                + "Projetos referenciam os ficheiros originais. Mantém-nos disponíveis no"
                + " dispositivo.",
            14,
            UiTheme.MUTED),
        -1,
        -2);
    UiTheme.sheet(c, "Definições", body);
  }
}
