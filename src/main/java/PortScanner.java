import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class PortScanner {

    private static final int[] PROBE_PORTS = {22, 80, 443, 445, 3389};

    public static InetAddress resoudreHote(String hote) throws UnknownHostException {
        return InetAddress.getByName(hote.trim());
    }

    public static String extrairePrefixe24(String ip) {
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            return null;
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    public static String trouverNomHote(InetAddress addr) {
        try {
            String nom = addr.getHostName();
            return (nom != null && !nom.isBlank()) ? nom : addr.getHostAddress();
        } catch (Exception e) {
            return addr.getHostAddress();
        }
    }

    public static String detecterMachineActive(String ip, int timeoutMs) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            if (addr.isReachable(timeoutMs)) {
                return trouverNomHote(addr);
            }
            for (int port : PROBE_PORTS) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(addr, port), timeoutMs);
                    return trouverNomHote(addr);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String scannerPortDetaille(InetAddress adresse, int port, int timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(adresse, port), timeout);
            socket.setSoTimeout(300);

            String banniere = lireBanniere(socket, port);
            String service = identifierService(port, banniere);

            if (!banniere.isEmpty()) {
                return "Port " + port + " : OUVERT (" + service + ") — " + banniere;
            }
            return "Port " + port + " : OUVERT (" + service + ")";
        } catch (Exception e) {
            return null;
        }
    }

    private static String lireBanniere(Socket socket, int port) {
        try {
            InputStream in = socket.getInputStream();
            if (port == 80 || port == 8080 || port == 8000) {
                OutputStream out = socket.getOutputStream();
                out.write("HEAD / HTTP/1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            byte[] buffer = new byte[128];
            int lus = in.read(buffer);
            if (lus > 0) {
                return new String(buffer, 0, lus, StandardCharsets.UTF_8)
                        .replace("\r", " ")
                        .replace("\n", " ")
                        .trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    private static String identifierService(int port, String banniere) {
        String b = banniere.toLowerCase();
        if (b.contains("ssh")) return "SSH";
        if (b.contains("http")) return "HTTP";
        if (b.contains("ftp")) return "FTP";
        if (b.contains("smtp")) return "SMTP";

        return switch (port) {
            case 21 -> "FTP";
            case 22 -> "SSH";
            case 23 -> "Telnet";
            case 25 -> "SMTP";
            case 53 -> "DNS";
            case 80 -> "HTTP";
            case 110 -> "POP3";
            case 143 -> "IMAP";
            case 443 -> "HTTPS";
            case 3306 -> "MySQL";
            case 5432 -> "PostgreSQL";
            case 8080 -> "HTTP-Alt";
            default -> "Service inconnu";
        };
    }

    public static void main(String[] args) {
        Scanner clavier = new Scanner(System.in);

        System.out.print("Machine à scanner (IP ou domaine) : ");
        String hote = clavier.nextLine();

        InetAddress adresse;
        try {
            adresse = resoudreHote(hote);
        } catch (UnknownHostException e) {
            System.out.println("Hôte introuvable : " + hote);
            clavier.close();
            return;
        }

        System.out.print("Port de début : ");
        int debut = clavier.nextInt();

        System.out.print("Port de fin : ");
        int fin = clavier.nextInt();

        int timeout = 400;
        int nbOuverts = 0;

        System.out.println("Scan de " + adresse.getHostAddress() + " (" + debut + " à " + fin + ")...");

        for (int port = debut; port <= fin; port++) {
            String ligne = scannerPortDetaille(adresse, port, timeout);
            if (ligne != null) {
                System.out.println(ligne);
                nbOuverts++;
            }
        }

        System.out.println("Scan terminé : " + nbOuverts + " port(s) ouvert(s).");
        clavier.close();
    }
}