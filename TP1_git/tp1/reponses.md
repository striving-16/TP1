# TP1 — SOAP et WSDL · Partie A (code-first)

## Étape 1 — Créer et lancer le service

**Q1.** `@WebService` (sur la classe `EmpruntService`) en fait un service web. `@WebFault` (sur `LivreIndisponibleException`) en fait une erreur déclarée dans le contrat : elle devient un `wsdl:fault` de `emprunterLivre`, avec l'élément `LivreIndisponible` dans le XSD.

**Q2.** JAXB crée les objets par réflexion quand il désérialise le XML (`newInstance()`), puis remplit les champs un par un. Sans constructeur vide, il ne peut pas instancier la classe et le service refuse de démarrer (`IllegalAnnotationsException` : « does not have a no-arg default constructor »).

## Étape 2 — Lire le WSDL généré

**Q1.** URL : `http://localhost:8080/bib/EmpruntService` (`soap:address`).
Opérations :
| Opération | Entrée | Sortie | Fault |
|---|---|---|---|
| `emprunterLivre` | message `emprunterLivre` → élément `tns:emprunterLivre` | `emprunterLivreResponse` | `LivreIndisponibleException` → élément `tns:LivreIndisponible` |
| `rendreLivre` | `rendreLivre` | `rendreLivreResponse` | — |

**Q2.** `style="document"` et `use="literal"` (document/literal *wrapped*). C'est du **SOAP 1.1** : le binding utilise le namespace `http://schemas.xmlsoap.org/wsdl/soap/` avec `transport="http://schemas.xmlsoap.org/soap/http"`.

**Q3.** `isbn` → `xs:string` (`minOccurs="0"`), `dateRetour` → `xs:string` aussi, puisque c'est un `String` en Java. La règle de format de l'ISBN **n'apparaît pas** : la regex existe seulement dans le code Java. Le contrat ne l'exprime pas, donc le client ne peut pas la connaître.

**Q4.** Non. Seul l'élément enveloppe porte un préfixe : `<bib:emprunterLivre>` (ou `<emp:…>` dans SoapUI). `<idAdherent>` et `<isbn>` n'en ont pas, car le schéma généré n'a pas `elementFormDefault="qualified"` : les éléments locaux ne sont pas qualifiés.

**Q5.** Dans l'onglet Raw :
- **HTTP** : la première ligne et les en-têtes (`POST /bib/EmpruntService HTTP/1.1`, `Content-Type: text/xml;charset=UTF-8`, `SOAPAction: ""`, puis `HTTP/1.1 200 OK` dans la réponse). C'est le **transport**.
- **XML** : tout le corps. C'est le **format** des données (balises, namespaces).
- **SOAP** : la structure `Envelope / Header / Body` (et `Fault`). C'est le **protocole de messagerie** (enveloppe, en-têtes extensibles, erreurs normalisées).
- **WSDL** : n'apparaît pas dans l'échange. C'est le **contrat** qui décrit à l'avance les opérations, les messages, les types XSD, le binding et l'adresse. SoapUI s'en est servi pour générer la requête.

Réponse obtenue : `emprunterLivreResponse` avec `<emprunt><numero>E-2026-0812</numero><dateRetour>…</dateRetour></emprunt>` (date du jour + 14 jours).

## Étape 3 — Envoyer des messages SOAP

Fichiers : `xml/01-emprunter.xml`, `xml/02-emprunter-12.xml`, `xml/04-rendre-corrige.xml`.
Avec `01-emprunter.xml` → `HTTP/1.1 200`, numéro `E-2026-0813` si SoapUI a déjà fait un emprunt avant.

**Q1.** En SOAP, l'URL ne désigne pas la ressource. Il n'y a qu'**un seul endpoint**, et l'opération est indiquée par l'élément du Body (et par `SOAPAction`). Il faut donc envoyer une enveloppe XML dans le corps de la requête : ça demande POST. HTTP sert seulement de tuyau de transport, sa sémantique (GET, idempotence, cache) n'est pas utilisée. Le binding SOAP 1.1 HTTP ne définit d'ailleurs que POST.

**Q2.** Non. Un bloc de Header **sans `mustUnderstand="1"`** est facultatif : si le destinataire ne le reconnaît pas, il peut l'ignorer. Ici, aucun handler ne traite `jeton`, donc Metro l'ignore et traite le Body normalement.

**Q3.** Réponse : `HTTP 500` avec une Fault **SOAP 1.1** de code `S:VersionMismatch`, du type « Couldn't create SOAP message. Expecting Envelope in namespace http://schemas.xmlsoap.org/soap/envelope/, but got http://www.w3.org/2003/05/soap-envelope ». Le binding lu à l'étape 2 est SOAP 1.1 (`wsdl/soap/`), donc l'endpoint n'accepte que l'enveloppe 1.1. Le namespace de l'Envelope identifie la version, et une version inconnue déclenche `VersionMismatch`.

**Q4.** Les cinq erreurs :
| # | Erreur | Correction |
|---|---|---|
| 1 | `<soap:envelope>` en minuscules (ne correspond pas à `</soap:Envelope>`) | `<soap:Envelope>` |
| 2 | `xmlns:bib="http://supnum.mr/bibliotheque"` | `http://supnum.mr/bib` |
| 3 | `</rendreLivre>` ne ferme pas `<bib:rendreLivre>` | `</bib:rendreLivre>` |
| 4 | Paramètre `<bib:numero>` : mauvais nom, et préfixé alors que les éléments sont non qualifiés | `<numeroEmprunt>` sans préfixe |
| 5 | `Header` placé après `Body` | `Header` avant `Body` |

Erreurs qui produisent une Fault : 1 et 3 (XML mal formé, Fault d'analyse) et 2 (Fault `Client` « Cannot find dispatch method for {http://supnum.mr/bibliotheque}rendreLivre »).

Erreurs **silencieuses** :
- **4** : JAXB ignore les éléments qu'il ne connaît pas. `numeroEmprunt` reçoit `null` et le service répond `penalite = 0` sans rien signaler. Après l'étape 4, ce cas donne la Fault « Numéro d'emprunt inconnu : null ».
- **5** : Metro lit le Body et ne vérifie pas l'ordre strict Header/Body. Le Header mal placé est simplement ignoré, ce qui n'est pas grave puisque `jeton` n'est pas obligatoire.

## Étape 4 — Faults

Note : `rendreLivre` est complété dans `EmpruntService.java` (Fault `Client` si le numéro ne commence pas par `E-2026-`). Fault 1.2 écrite à la main : `xml/03-fault-sender-12.xml` (validée avec `xmllint`). Le fichier `xml/01b-emprunter-mustunderstand.xml` contient la variante avec `mustUnderstand`.

**Q1.**
- Cas 1 (ISBN `978-2-10`) : `faultcode = S:Client`, `faultstring = "ISBN mal formé : 978-2-10"`, **HTTP 500**.
- Cas 2 (ISBN `978-2-10-074444-4`) : `faultcode = S:Server`, `faultstring = "Livre indisponible"`, **HTTP 500**.

En SOAP 1.1 sur HTTP, toute Fault est renvoyée avec le code 500.

**Q2.** Le `<detail>` contient l'objet `faultInfo` sérialisé :
```xml
<detail>
  <ns2:LivreIndisponible xmlns:ns2="http://supnum.mr/bib">
    <isbn>978-2-10-074444-4</isbn>
    <retourPrevu>2026-12-02</retourPrevu>
  </ns2:LivreIndisponible>
</detail>
```
Sa structure est décrite dans le WSDL par `wsdl:fault name="LivreIndisponibleException"` (dans le portType et le binding). Celui-ci pointe vers le `wsdl:message LivreIndisponibleException`, dont la part référence l'élément `tns:LivreIndisponible` du XSD (type complexe `livreIndisponibleInfo` avec `isbn` et `retourPrevu`).

**Q3.** JAX-WS a choisi **`Server`**, car il mappe toutes les exceptions `@WebFault` sur `Server`. Pourtant ce n'est pas une panne du serveur : c'est une erreur **métier**, causée par la requête (le livre demandé est déjà sorti). J'aurais choisi **`Client`** : renvoyer la même requête donnera le même résultat, et c'est au client de changer quelque chose (choisir un autre livre ou attendre le `retourPrevu`).

**Q4.**
- `mustUnderstand="1"` : le serveur n'a aucun handler pour `{http://supnum.mr/bib}jeton`, il doit donc **refuser tout le message**. Il répond `HTTP 500` avec `faultcode = S:MustUnderstand` et `faultstring` « MustUnderstand headers:[{http://supnum.mr/bib}jeton] are not understood ». L'opération n'est pas exécutée.
- `mustUnderstand="0"` : c'est le comportement par défaut. Le header est facultatif, il est ignoré et la requête est traitée normalement (200).

**Q5.** Différences entre la Fault 1.2 écrite à la main et les Faults 1.1 reçues :
| SOAP 1.1 (reçue) | SOAP 1.2 (écrite) |
|---|---|
| namespace `http://schemas.xmlsoap.org/soap/envelope/` | `http://www.w3.org/2003/05/soap-envelope` |
| `<faultcode>` en texte simple | `<soap:Code><soap:Value>`, extensible par `<soap:Subcode>` |
| codes `Client` / `Server` | codes `Sender` / `Receiver` |
| `<faultstring>` (une seule langue) | `<soap:Reason><soap:Text xml:lang="fr">` (plusieurs langues possibles) |
| `<detail>` non qualifié | `<soap:Detail>` qualifié |
| HTTP 500 dans tous les cas | HTTP 400 pour `Sender`, 500 pour `Receiver` |
| `Content-Type: text/xml` | `application/soap+xml` |
