public class EnergyRuleMatch extends RuleMatch {
    // constructors
    public EnergyRuleMatch(EnergyRulePattern p) { super(p); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board) { super(p,board,0); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board,String a) { super(p,board,0,a); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board,String a,String b) { super(p,board,0,a,b); }

    // private methods
    private final EnergyRulePattern energyPattern() { return (EnergyRulePattern) pattern; }

    // public methods
    public final double E() { return energyPattern().E; }

    // override expandDir()
    protected String expandDir (String s) { return s; }
}
