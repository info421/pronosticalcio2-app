# Pronosticalcio 2 — app Android

L'app apre il sito di Pronosticalcio 2 (worker Cloudflare) a schermo intero sul telefono.

- La password del sito si chiede una volta sola e resta salvata nell'app.
- "Copia INSTA" apre la condivisione di Android (WhatsApp, Note, Drive…).
- I link esterni si aprono nel browser.
- Tornando nell'app dopo più di 10 minuti la pagina si ricarica.
- All'avvio controlla se su GitHub c'è una versione nuova e propone l'aggiornamento.

## Come si installa
1. Dal telefono apri la pagina **Releases** di questo repository e scarica `Pronosticalcio2.apk`.
2. Aprilo e consenti l'installazione da questa fonte quando Android lo chiede.

## Come si aggiorna
Ogni modifica caricata sul ramo `main` fa partire GitHub Actions, che compila l'APK e lo pubblica nella release `latest`.
L'app se ne accorge da sola al successivo avvio.

Il sito (le partite, le regole, i pronostici) si aggiorna dal worker: per quello non serve aggiornare l'app.
