public class EnergyRuleMatch extends RuleMatch {
    // constructors
    public EnergyRuleMatch(EnergyRulePattern p) { super(p); }
    public EnergyRuleMatch(EnergyRulePattern p,String a) { super(p); bindSource(a); }
    public EnergyRuleMatch(EnergyRulePattern p,String a,String b) { super(p); bindSource(a); bindSource(b); }

    // private methods
    private final EnergyRulePattern energyPattern() { return (EnergyRulePattern) pattern; }

    // public methods
    public final double E() { return energyPattern().E; }
}
