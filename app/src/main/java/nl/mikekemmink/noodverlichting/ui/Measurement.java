
// Replace package name below with your actual package
package nl.mikekemmink.noodverlichting.ui;

import java.io.Serializable;

public class Measurement implements Serializable {
    private String kastnaam;
    private Double verdeelinrichting, l1, l2, l3, n, pe;

    public Measurement(String kastnaam, Double verdeelinrichting, Double l1, Double l2, Double l3, Double n) {
        this.kastnaam = kastnaam;
        this.verdeelinrichting = verdeelinrichting;
        this.l1 = l1;
        this.l2 = l2;
        this.l3 = l3;
        this.n = n;
        this.pe = pe;
    }

    public String getKastnaam() { return kastnaam; }
    public Double getVerdeelinrichting() { return verdeelinrichting; }
    public Double getL1() { return l1; }
    public Double getL2() { return l2; }
    public Double getL3() { return l3; }
    public Double getN() { return n; }
    public Double getPe() { return pe; }
}
