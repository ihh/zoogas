public class EnergyRuleMatch extends RuleMatch {
    // constructors
    public EnergyRuleMatch(EnergyRulePattern p) { super(p); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board,int dir) { super(p,board,dir); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board,int dir,String a) { super(p,board,dir,a); }
    public EnergyRuleMatch(EnergyRulePattern p,Board board,int dir,String a,String b) { super(p,board,dir,a,b); }

    // private methods
    private final EnergyRulePattern energyPattern() { return (EnergyRulePattern) pattern; }

    // public methods
    public final double E() { return energyPattern().E; }
    
}
