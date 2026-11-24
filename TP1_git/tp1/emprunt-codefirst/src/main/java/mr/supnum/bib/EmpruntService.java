package mr.supnum.bib;

import jakarta.jws.*;
import jakarta.xml.soap.*;
import jakarta.xml.ws.soap.SOAPFaultException;
import javax.xml.namespace.QName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

@WebService(serviceName = "EmpruntService", portName = "EmpruntPort",
            targetNamespace = "http://supnum.mr/bib")
public class EmpruntService {

    private static final Pattern ISBN = Pattern.compile("97[89]-\\d-\\d{2,5}-\\d{2,6}-\\d");
    private final Map<String, LocalDate> sortis =
            new ConcurrentHashMap<>(Map.of("978-2-10-074444-4", LocalDate.of(2026, 12, 2)));
    private final AtomicInteger compteur = new AtomicInteger(812);

    @WebMethod @WebResult(name = "emprunt")
    public Emprunt emprunterLivre(@WebParam(name = "idAdherent") String idAdherent,
                                  @WebParam(name = "isbn") String isbn)
            throws LivreIndisponibleException {
        if (isbn == null || !ISBN.matcher(isbn).matches())
            throw faultClient("ISBN mal formé : " + isbn);
        LocalDate retour = sortis.get(isbn);
        if (retour != null)
            throw new LivreIndisponibleException("Livre indisponible",
                    new LivreIndisponibleInfo(isbn, retour.toString()));
        LocalDate dateRetour = LocalDate.now().plusDays(14);
        sortis.put(isbn, dateRetour);
        return new Emprunt(String.format("E-2026-%04d", compteur.getAndIncrement()), dateRetour.toString());
    }

    @WebMethod @WebResult(name = "penalite")
    public BigDecimal rendreLivre(@WebParam(name = "numeroEmprunt") String numeroEmprunt) {
        if (numeroEmprunt == null || !numeroEmprunt.startsWith("E-2026-"))
            throw faultClient("Numéro d'emprunt inconnu : " + numeroEmprunt);
        return BigDecimal.ZERO;
    }

    private static SOAPFaultException faultClient(String message) {
        try {
            SOAPFault f = SOAPFactory.newInstance().createFault(message,
                    new QName(SOAPConstants.URI_NS_SOAP_1_1_ENVELOPE, "Client"));
            return new SOAPFaultException(f);
        } catch (SOAPException e) { throw new IllegalStateException(e); }
    }
}
