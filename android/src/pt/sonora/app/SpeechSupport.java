package pt.sonora.app;

import java.util.*;

/** Empty metadata is unknown, not proof that an installed recognition model is missing. */
final class SpeechSupport {
  static boolean compatible(String requested, List<String> installed) {
    String want = normalize(requested);
    for (String tag : installed) {
      String value = normalize(tag);
      if (value.equals(want) || value.equals(want.split("-")[0])) return true;
    }
    return false;
  }

  static String normalize(String language) {
    return language == null ? "" : language.replace('_', '-').toLowerCase(Locale.ROOT);
  }

  static boolean canAttempt(String language, List<String> installed) {
    return installed == null || installed.isEmpty() || compatible(language, installed);
  }

  static String error(int code) {
    switch (code) {
      case 1:
        return "O serviço excedeu o tempo de comunicação.";
      case 2:
        return "O serviço local reportou um erro de rede. Não foi feita transcrição online.";
      case 3:
        return "O serviço recusou a entrada de áudio do ficheiro.";
      case 4:
        return "O serviço de reconhecimento não respondeu corretamente.";
      case 5:
        return "O serviço não aceitou esta sessão de transcrição de ficheiro.";
      case 6:
        return "O serviço não detetou fala no áudio.";
      case 7:
        return "Não foi possível reconhecer fala. Canto ou música instrumental podem não ser"
                   + " transcritos.";
      case 8:
        return "O reconhecimento está ocupado. Fecha outras aplicações que usem reconhecimento e"
                   + " tenta novamente.";
      case 9:
        return "Falta autorização de reconhecimento/microfone nas permissões do Sonora Lab.";
      case 10:
        return "O serviço recebeu demasiados pedidos. Aguarda um momento.";
      case 11:
        return "O serviço de reconhecimento foi desligado pelo Android.";
      case 12:
        return "O serviço não suporta este idioma.";
      case 13:
        return "O modelo deste idioma ainda não está disponível no serviço local.";
      case 14:
        return "Este serviço não fornece uma verificação de compatibilidade.";
      case 15:
        return "O serviço não permite acompanhar o download do modelo.";
      default:
        return "O serviço de reconhecimento devolveu o erro " + code + ".";
    }
  }
}
