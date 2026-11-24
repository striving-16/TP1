package mr.supnum.bib;
import jakarta.xml.ws.Endpoint;
public class Serveur {
    public static void main(String[] args) {
        String url = "http://localhost:8080/bib/EmpruntService";
        Endpoint.publish(url, new EmpruntService());
        System.out.println("WSDL : " + url + "?wsdl");
    }
}
