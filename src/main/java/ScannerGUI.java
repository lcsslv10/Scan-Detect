import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ScannerGUI {

    private static final Color BG_DARK = new Color(12, 14, 18);
    private static final Color BG_PANEL = new Color(20, 24, 32);
    private static final Color BG_INPUT = new Color(15, 18, 24);
    private static final Color BORDER_COLOR = new Color(38, 46, 60);
    private static final Color TEXT_MAIN = new Color(238, 241, 246);
    private static final Color TEXT_MUTED = new Color(152, 162, 179);
    private static final Color ACCENT_BLUE = new Color(59, 130, 246);
    private static final Color ACCENT_GREEN = new Color(16, 185, 129);
    private static final Color ACCENT_RED = new Color(239, 68, 68);

    private static JTextField stylerChamp(String valeur, int colonnes) {
        JTextField champ = new JTextField(valeur, colonnes);
        champ.setBackground(BG_INPUT);
        champ.setForeground(TEXT_MAIN);
        champ.setCaretColor(TEXT_MAIN);
        champ.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        champ.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                new EmptyBorder(6, 8, 6, 8)
        ));
        return champ;
    }

    private static JButton stylerBouton(String texte, Color fond, Color texteCouleur) {
        JButton bouton = new JButton(texte);
        bouton.setBackground(fond);
        bouton.setForeground(texteCouleur);
        bouton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        bouton.setFocusPainted(false);
        bouton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        bouton.setBorder(new EmptyBorder(8, 14, 8, 14));
        return bouton;
    }

    private static JLabel stylerLabel(String texte) {
        JLabel label = new JLabel(texte);
        label.setForeground(TEXT_MUTED);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return label;
    }

    public static void scannerReseau(
            String prefixe24,
            int timeout,
            int nbThreads,
            AtomicBoolean arreter,
            DefaultTableModel modeleHotes,
            JTextArea zoneLogs,
            JProgressBar barre,
            JLabel statut,
            JButton boutonScanPorts,
            JButton boutonScanReseau,
            JButton boutonStop
    ) {
        new Thread(() -> {
            int total = 254;
            AtomicInteger traites = new AtomicInteger(0);
            AtomicInteger trouves = new AtomicInteger(0);

            SwingUtilities.invokeLater(() -> {
                modeleHotes.setRowCount(0);
                zoneLogs.append("\n--- Découverte réseau sur " + prefixe24 + ".1-254 ---\n");
                statut.setText("Recherche des machines sur " + prefixe24 + ".0/24...");
            });

            ExecutorService pool = Executors.newFixedThreadPool(nbThreads);

            for (int i = 1; i <= 254; i++) {
                if (arreter.get()) {
                    break;
                }
                String ip = prefixe24 + "." + i;
                pool.submit(() -> {
                    if (arreter.get()) {
                        return;
                    }
                    String nomHote = PortScanner.detecterMachineActive(ip, timeout);
                    if (nomHote != null) {
                        trouves.incrementAndGet();
                        SwingUtilities.invokeLater(() -> {
                            modeleHotes.addRow(new Object[]{ip, nomHote, "Actif"});
                            zoneLogs.append("[+] Hôte actif : " + ip + " (" + nomHote + ")\n");
                        });
                    }
                    int done = traites.incrementAndGet();
                    SwingUtilities.invokeLater(() -> {
                        barre.setValue(done);
                        statut.setText("Réseau : " + done + " / " + total + " IP testées | Actives : " + trouves.get());
                    });
                });
            }

            pool.shutdown();
            try {
                pool.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
            }

            boolean stoppe = arreter.get();
            int totalTrouves = trouves.get();

            SwingUtilities.invokeLater(() -> {
                if (stoppe) {
                    zoneLogs.append("Scan réseau arrêté.\n");
                    statut.setText("Réseau arrêté (" + totalTrouves + " active(s))");
                } else {
                    zoneLogs.append("Découverte terminée : " + totalTrouves + " machine(s) active(s).\n");
                    statut.setText("Terminé — " + totalTrouves + " machine(s) trouvée(s)");
                }
                boutonScanPorts.setEnabled(true);
                boutonScanReseau.setEnabled(true);
                boutonStop.setEnabled(false);
            });
        }).start();
    }

    public static void lancerScan(
            String hote,
            int debut,
            int fin,
            int timeout,
            int nbThreads,
            AtomicBoolean arreter,
            JTextArea zone,
            JProgressBar barre,
            JLabel statut,
            JButton boutonScanPorts,
            JButton boutonScanReseau,
            JButton boutonStop
    ) {
        new Thread(() -> {
            InetAddress adresse;
            try {
                adresse = PortScanner.resoudreHote(hote);
            } catch (UnknownHostException ex) {
                SwingUtilities.invokeLater(() -> {
                    zone.append("Erreur : hôte introuvable (" + hote + ")\n");
                    statut.setText("Erreur DNS");
                    boutonScanPorts.setEnabled(true);
                    boutonScanReseau.setEnabled(true);
                    boutonStop.setEnabled(false);
                });
                return;
            }

            int totalPorts = fin - debut + 1;
            AtomicInteger traites = new AtomicInteger(0);
            AtomicInteger ouverts = new AtomicInteger(0);

            SwingUtilities.invokeLater(() -> {
                zone.append("\n--- Scan de " + hote + " [" + adresse.getHostAddress() + "] ports " + debut + "-" + fin + " ---\n");
                statut.setText("Scan en cours sur " + adresse.getHostAddress() + "...");
            });

            ExecutorService pool = Executors.newFixedThreadPool(nbThreads);

            for (int port = debut; port <= fin; port++) {
                if (arreter.get()) {
                    break;
                }
                int portActuel = port;
                pool.submit(() -> {
                    if (arreter.get()) {
                        return;
                    }
                    String resultat = PortScanner.scannerPortDetaille(adresse, portActuel, timeout);
                    if (resultat != null) {
                        ouverts.incrementAndGet();
                        SwingUtilities.invokeLater(() -> zone.append("[+] " + resultat + "\n"));
                    }
                    int progression = traites.incrementAndGet();
                    SwingUtilities.invokeLater(() -> {
                        barre.setValue(progression);
                        statut.setText("Ports : " + progression + " / " + totalPorts + " | Ouverts : " + ouverts.get());
                    });
                });
            }

            pool.shutdown();
            try {
                pool.awaitTermination(15, TimeUnit.MINUTES);
            } catch (InterruptedException ignored) {
            }

            boolean stoppe = arreter.get();
            int totalOuverts = ouverts.get();

            SwingUtilities.invokeLater(() -> {
                if (stoppe) {
                    zone.append("Scan arrêté par l'utilisateur.\n");
                    statut.setText("Arrêté (" + totalOuverts + " ouvert(s))");
                } else {
                    zone.append("Scan terminé : " + totalOuverts + " port(s) ouvert(s).\n");
                    statut.setText("Terminé (" + totalOuverts + " ouvert(s))");
                }
                boutonScanPorts.setEnabled(true);
                boutonScanReseau.setEnabled(true);
                boutonStop.setEnabled(false);
            });
        }).start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame fenetre = new JFrame("Scan & Detect");
            fenetre.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            fenetre.getContentPane().setBackground(BG_DARK);
            fenetre.setLayout(new BorderLayout(10, 10));

            JTextField champHote = stylerChamp("127.0.0.1", 13);
            JTextField champDebut = stylerChamp("1", 5);
            JTextField champFin = stylerChamp("1024", 5);
            JTextField champTimeout = stylerChamp("300", 5);
            JTextField champThreads = stylerChamp("50", 4);
            JTextField champSousReseau = stylerChamp("192.168.1", 11);

            JButton boutonScanPorts = stylerBouton("Scanner ports", ACCENT_BLUE, Color.WHITE);
            JButton boutonScanReseau = stylerBouton("Machines réseau", ACCENT_GREEN, Color.WHITE);
            JButton boutonStop = stylerBouton("Arrêter", ACCENT_RED, Color.WHITE);
            JButton boutonEffacer = stylerBouton("Effacer", BG_PANEL, TEXT_MAIN);
            boutonEffacer.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(7, 13, 7, 13)
            ));
            boutonStop.setEnabled(false);

            JPanel carteConfig = new JPanel(new BorderLayout(10, 8));
            carteConfig.setBackground(BG_PANEL);
            carteConfig.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(10, 12, 10, 12)
            ));

            JPanel ligneCible = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            ligneCible.setBackground(BG_PANEL);
            ligneCible.add(stylerLabel("Cible :"));
            ligneCible.add(champHote);
            ligneCible.add(stylerLabel("Ports :"));
            ligneCible.add(champDebut);
            ligneCible.add(stylerLabel("à"));
            ligneCible.add(champFin);
            ligneCible.add(stylerLabel("Timeout (ms) :"));
            ligneCible.add(champTimeout);
            ligneCible.add(stylerLabel("Threads :"));
            ligneCible.add(champThreads);

            JPanel ligneReseau = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            ligneReseau.setBackground(BG_PANEL);
            ligneReseau.add(stylerLabel("Sous-réseau /24 :"));
            ligneReseau.add(champSousReseau);
            ligneReseau.add(boutonScanReseau);

            JPanel ligneBoutons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
            ligneBoutons.setBackground(BG_PANEL);
            ligneBoutons.add(boutonScanPorts);
            ligneBoutons.add(boutonStop);
            ligneBoutons.add(boutonEffacer);

            JPanel basConfig = new JPanel(new BorderLayout());
            basConfig.setBackground(BG_PANEL);
            basConfig.add(ligneReseau, BorderLayout.WEST);
            basConfig.add(ligneBoutons, BorderLayout.EAST);

            carteConfig.add(ligneCible, BorderLayout.NORTH);
            carteConfig.add(basConfig, BorderLayout.SOUTH);

            DefaultTableModel modeleHotes = new DefaultTableModel(new Object[]{"Adresse IP", "Nom d'hôte", "État"}, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            JTable tableHotes = new JTable(modeleHotes);
            tableHotes.setBackground(BG_INPUT);
            tableHotes.setForeground(TEXT_MAIN);
            tableHotes.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            tableHotes.setRowHeight(24);
            tableHotes.setGridColor(BORDER_COLOR);
            tableHotes.setSelectionBackground(new Color(37, 99, 235));
            tableHotes.setSelectionForeground(Color.WHITE);
            tableHotes.getTableHeader().setBackground(BG_PANEL);
            tableHotes.getTableHeader().setForeground(TEXT_MUTED);
            tableHotes.getTableHeader().setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

            tableHotes.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting() && tableHotes.getSelectedRow() != -1) {
                    String ipSelectionnee = modeleHotes.getValueAt(tableHotes.getSelectedRow(), 0).toString();
                    champHote.setText(ipSelectionnee);
                }
            });

            JPanel panneauHotes = new JPanel(new BorderLayout(0, 6));
            panneauHotes.setBackground(BG_PANEL);
            panneauHotes.setPreferredSize(new Dimension(340, 0));
            panneauHotes.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(10, 10, 10, 10)
            ));
            JLabel titreHotes = new JLabel("Machines détectées (clic = sélectionner)");
            titreHotes.setForeground(TEXT_MAIN);
            titreHotes.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            JScrollPane scrollHotes = new JScrollPane(tableHotes);
            scrollHotes.getViewport().setBackground(BG_INPUT);
            scrollHotes.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
            panneauHotes.add(titreHotes, BorderLayout.NORTH);
            panneauHotes.add(scrollHotes, BorderLayout.CENTER);

            JTextArea zoneResultats = new JTextArea();
            zoneResultats.setEditable(false);
            zoneResultats.setBackground(BG_INPUT);
            zoneResultats.setForeground(TEXT_MAIN);
            zoneResultats.setCaretColor(TEXT_MAIN);
            zoneResultats.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            zoneResultats.setBorder(new EmptyBorder(8, 10, 8, 10));

            JPanel panneauLogs = new JPanel(new BorderLayout(0, 6));
            panneauLogs.setBackground(BG_PANEL);
            panneauLogs.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(10, 10, 10, 10)
            ));
            JLabel titreLogs = new JLabel("Résultats du scan");
            titreLogs.setForeground(TEXT_MAIN);
            titreLogs.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
            JScrollPane scrollLogs = new JScrollPane(zoneResultats);
            scrollLogs.getViewport().setBackground(BG_INPUT);
            scrollLogs.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
            panneauLogs.add(titreLogs, BorderLayout.NORTH);
            panneauLogs.add(scrollLogs, BorderLayout.CENTER);

            JSplitPane splitCentre = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, panneauHotes, panneauLogs);
            splitCentre.setBackground(BG_DARK);
            splitCentre.setBorder(null);
            splitCentre.setDividerSize(8);

            JPanel carteBas = new JPanel(new BorderLayout(8, 6));
            carteBas.setBackground(BG_PANEL);
            carteBas.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER_COLOR, 1),
                    new EmptyBorder(10, 12, 10, 12)
            ));

            JProgressBar barre = new JProgressBar();
            barre.setStringPainted(true);
            barre.setBackground(BG_INPUT);
            barre.setForeground(ACCENT_BLUE);
            barre.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

            JLabel statut = new JLabel("Prêt");
            statut.setForeground(TEXT_MUTED);
            statut.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

            carteBas.add(barre, BorderLayout.CENTER);
            carteBas.add(statut, BorderLayout.SOUTH);

            JPanel conteneurPrincipal = new JPanel(new BorderLayout(10, 10));
            conteneurPrincipal.setBackground(BG_DARK);
            conteneurPrincipal.setBorder(new EmptyBorder(10, 10, 10, 10));
            conteneurPrincipal.add(carteConfig, BorderLayout.NORTH);
            conteneurPrincipal.add(splitCentre, BorderLayout.CENTER);
            conteneurPrincipal.add(carteBas, BorderLayout.SOUTH);

            AtomicBoolean arreter = new AtomicBoolean(false);

            boutonScanReseau.addActionListener(e -> {
                String prefixe = champSousReseau.getText().trim();
                if (PortScanner.extrairePrefixe24(prefixe + ".1") == null) {
                    zoneResultats.append("Sous-réseau invalide (ex: 192.168.1 ou 10.0.0).\n");
                    return;
                }

                int timeout;
                int nbThreads;
                try {
                    timeout = Integer.parseInt(champTimeout.getText().trim());
                    nbThreads = Integer.parseInt(champThreads.getText().trim());
                } catch (NumberFormatException ex) {
                    zoneResultats.append("Timeout et threads doivent être des nombres entiers.\n");
                    return;
                }

                arreter.set(false);
                boutonScanPorts.setEnabled(false);
                boutonScanReseau.setEnabled(false);
                boutonStop.setEnabled(true);

                barre.setMinimum(0);
                barre.setMaximum(254);
                barre.setValue(0);

                scannerReseau(
                        prefixe,
                        timeout,
                        nbThreads,
                        arreter,
                        modeleHotes,
                        zoneResultats,
                        barre,
                        statut,
                        boutonScanPorts,
                        boutonScanReseau,
                        boutonStop
                );
            });

            boutonScanPorts.addActionListener(e -> {
                String hote = champHote.getText().trim();
                if (hote.isEmpty()) {
                    zoneResultats.append("Veuillez saisir une machine ou une IP.\n");
                    return;
                }

                int debut;
                int fin;
                int timeout;
                int nbThreads;

                try {
                    debut = Integer.parseInt(champDebut.getText().trim());
                    fin = Integer.parseInt(champFin.getText().trim());
                    timeout = Integer.parseInt(champTimeout.getText().trim());
                    nbThreads = Integer.parseInt(champThreads.getText().trim());
                } catch (NumberFormatException ex) {
                    zoneResultats.append("Erreur : ports, timeout et threads doivent être des entiers.\n");
                    return;
                }

                if (debut < 1 || fin > 65535 || debut > fin) {
                    zoneResultats.append("Plage de ports invalide (1 à 65535, début <= fin).\n");
                    return;
                }
                if (timeout < 50 || nbThreads < 1 || nbThreads > 500) {
                    zoneResultats.append("Timeout >= 50 ms et threads entre 1 et 500.\n");
                    return;
                }

                arreter.set(false);
                boutonScanPorts.setEnabled(false);
                boutonScanReseau.setEnabled(false);
                boutonStop.setEnabled(true);

                int totalPorts = fin - debut + 1;
                barre.setMinimum(0);
                barre.setMaximum(totalPorts);
                barre.setValue(0);

                lancerScan(
                        hote,
                        debut,
                        fin,
                        timeout,
                        nbThreads,
                        arreter,
                        zoneResultats,
                        barre,
                        statut,
                        boutonScanPorts,
                        boutonScanReseau,
                        boutonStop
                );
            });

            boutonStop.addActionListener(e -> {
                arreter.set(true);
                boutonStop.setEnabled(false);
                statut.setText("Arrêt en cours...");
            });

            boutonEffacer.addActionListener(e -> {
                zoneResultats.setText("");
                modeleHotes.setRowCount(0);
                barre.setValue(0);
                statut.setText("Prêt");
            });

            fenetre.add(conteneurPrincipal, BorderLayout.CENTER);
            fenetre.setSize(960, 560);
            fenetre.setMinimumSize(new Dimension(820, 460));
            fenetre.setLocationRelativeTo(null);
            fenetre.setVisible(true);
        });
    }
}