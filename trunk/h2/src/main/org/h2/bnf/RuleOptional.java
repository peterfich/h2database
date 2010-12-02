/*
 * Copyright 2004-2010 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.bnf;

import java.util.HashMap;

/**
 * Represents an optional BNF rule.
 */
public class RuleOptional implements Rule {
    private Rule rule;
    private boolean mapSet;

    RuleOptional(Rule rule) {
        this.rule = rule;
    }

    public void accept(BnfVisitor visitor) {
        visitor.visitRuleOptional(rule);
    }

    public void setLinks(HashMap<String, RuleHead> ruleMap) {
        if (!mapSet) {
            rule.setLinks(ruleMap);
            mapSet = true;
        }
    }
    public boolean autoComplete(Sentence sentence) {
        if (sentence.shouldStop()) {
            return false;
        }
        rule.autoComplete(sentence);
        return true;
    }

}
