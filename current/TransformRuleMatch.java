public class TransformRuleMatch extends RuleMatch {
    // constructors
    public TransformRuleMatch(TransformRulePattern p) { super(p); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir) { super(p,board,dir); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir,String a) { super(p,board,dir,a); }
    public TransformRuleMatch(TransformRulePattern p,Board board,int dir,String a,String b) { super(p,board,dir,a,b); }

    // private methods
    private final TransformRulePattern transformPattern() { return (TransformRulePattern) pattern; }

    // public methods
    public final String C() { return expandRHS(transformPattern().C); }
    public final String D() { return expandRHS(transformPattern().D); }
    public final String V() { return expandRHS(transformPattern().V); }
    public final double P() { return transformPattern().P; }
}
