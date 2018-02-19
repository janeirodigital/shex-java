/*******************************************************************************
 * Copyright (C) 2018 Université de Lille - Inria
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package fr.univLille.cristal.shex.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.univLille.cristal.shex.schema.ShapeExprLabel;
import fr.univLille.cristal.shex.schema.TripleExprLabel;
import fr.univLille.cristal.shex.schema.abstrsynt.EachOf;
import fr.univLille.cristal.shex.schema.abstrsynt.EmptyTripleExpression;
import fr.univLille.cristal.shex.schema.abstrsynt.OneOf;
import fr.univLille.cristal.shex.schema.abstrsynt.RepeatedTripleExpression;
import fr.univLille.cristal.shex.schema.abstrsynt.Shape;
import fr.univLille.cristal.shex.schema.abstrsynt.TripleConstraint;
import fr.univLille.cristal.shex.schema.abstrsynt.TripleExpr;
import fr.univLille.cristal.shex.schema.abstrsynt.TripleExprRef;
import fr.univLille.cristal.shex.schema.analysis.TripleExpressionVisitor;
import fr.univLille.cristal.shex.util.Interval;
import fr.univLille.cristal.shex.util.RDFFactory;

public class SORBEGenerator {
	private final static RDFFactory RDF_FACTORY = RDFFactory.getInstance();
	private static int tripleLabelNb = 0;
	private static String TRIPLE_LABEL_PREFIX = "LABEL_FOR_SORBE_GENERATED";
	
	private Map<ShapeExprLabel,TripleExpr> sorbeMap;
	
	public SORBEGenerator() {
		this.sorbeMap=new HashMap<ShapeExprLabel,TripleExpr>();
	}
	
	// Get ride of the tripleExprRef by copying
	// Check that empty is not under a plus
	public TripleExpr getSORBETripleExpr(Shape shape) {
		if (this.sorbeMap.containsKey(shape.getId()))
			return this.sorbeMap.get(shape.getId());
		TripleExpr result = generateTripleExpr(shape.getTripleExpression());
		this.sorbeMap.put(shape.getId(), result);
		return result;
	}
	
	
	private TripleExpr generateTripleExpr(TripleExpr expr) {
		if (expr instanceof TripleExprRef) 
			return generateTripleExpr(((TripleExprRef) expr).getTripleExp());
		if (expr instanceof EachOf)
			return generateEachOf((EachOf) expr);
		if (expr instanceof OneOf)
			return generateOneOf((OneOf) expr);
		if (expr instanceof TripleConstraint)
			return generateTripleConstraint((TripleConstraint) expr);
		if (expr instanceof RepeatedTripleExpression)
			return generateRepeatedTripleExpression((RepeatedTripleExpression) expr);
		if (expr instanceof EmptyTripleExpression)
			return generateEmptyTripleExpression((EmptyTripleExpression) expr);
		System.err.println(expr.getClass());
		return null;
	}
	
	private TripleExpr generateEmptyTripleExpression(EmptyTripleExpression expr ) {
		TripleExpr result = new EmptyTripleExpression();
		setTripleLabel(result);
		return result;
	}
	
	
	private TripleExpr generateEachOf(EachOf expr) {
		List<TripleExpr> newSubExprs = new ArrayList<TripleExpr>();
		for (TripleExpr subExpr:expr.getSubExpressions()) {
			newSubExprs.add(generateTripleExpr(subExpr));
		}
		TripleExpr result = new EachOf(newSubExprs);
		setTripleLabel(result);
		return result;
	}
	

	private TripleExpr generateOneOf(OneOf expr) {
		List<TripleExpr> newSubExprs = new ArrayList<TripleExpr>();
		for (TripleExpr subExpr:expr.getSubExpressions()) {
			newSubExprs.add(generateTripleExpr(subExpr));
		}
		TripleExpr result = new OneOf(newSubExprs);
		setTripleLabel(result);
		return result;
	}
	
	
	private TripleExpr generateTripleConstraint(TripleConstraint expr) {
		TripleExpr result = expr.clone();
		setTripleLabel(result);
		return result;
	}
	

	private TripleExpr generateRepeatedTripleExpression(RepeatedTripleExpression expr) {
		CheckIfContainsEmpty visitor = new CheckIfContainsEmpty();
		expr.accept(visitor);
		if (expr.getCardinality().equals(Interval.PLUS) & visitor.result) {
			TripleExpr result = new RepeatedTripleExpression(generateTripleExpr(expr.getSubExpression()),Interval.STAR);
			setTripleLabel(result);
			return result;
		} else if(expr.getCardinality().equals(Interval.PLUS)
				  || expr.getCardinality().equals(Interval.STAR)
				  || expr.getCardinality().equals(Interval.OPT)){
			TripleExpr result = new RepeatedTripleExpression(generateTripleExpr(expr.getSubExpression()),expr.getCardinality());
			setTripleLabel(result);
			return result;
		}else {
			Interval card = expr.getCardinality();
			int nbClones = 0, nbOptClones = 0;
			List<TripleExpr> clones = new ArrayList<TripleExpr>();

			if (card.max == Interval.UNBOUND) {
				nbClones = card.min -1;
				TripleExpr tmp = new RepeatedTripleExpression(generateTripleExpr(expr.getSubExpression()), Interval.PLUS);
				setTripleLabel(tmp);
				clones.add(tmp);
			}else {
				nbClones = card.min;
				nbOptClones = card.max - card.min;
			}

			for (int i=0; i<nbClones;i++) {
				clones.add(generateTripleExpr(expr.getSubExpression()));	
			}
			for (int i=0; i<nbOptClones;i++) {
				TripleExpr tmp = new RepeatedTripleExpression(generateTripleExpr(expr.getSubExpression()), Interval.OPT);
				setTripleLabel(tmp);
				clones.add(tmp);
			}
			TripleExpr result = new EachOf(clones);
			setTripleLabel(result);
			return result;
		}
	}
	

	private void setTripleLabel(TripleExpr triple) {
		triple.setId(new TripleExprLabel(RDF_FACTORY.createBNode(TRIPLE_LABEL_PREFIX+"_"+tripleLabelNb),true));
		tripleLabelNb++;
	}
	
	class CheckIfContainsEmpty extends TripleExpressionVisitor<Boolean>{

		private boolean result ;

		public CheckIfContainsEmpty() {
		}

		@Override
		public Boolean getResult() {
			return result;
		}

		@Override
		public void visitTripleConstraint(TripleConstraint tc, Object... arguments) {
			result = false;
		}

		@Override
		public void visitEmpty(EmptyTripleExpression expr, Object[] arguments) {
			result = false;
		}

		@Override
		public void visitEachOf(EachOf expr, Object... arguments) {
			for (TripleExpr subExpr : expr.getSubExpressions()) {
				subExpr.accept(this, arguments);
				if (!result) 
					return;
			}
		}

		@Override
		public void visitOneOf(OneOf expr, Object... arguments) {
			for (TripleExpr subExpr : expr.getSubExpressions()) {
				subExpr.accept(this, arguments);
				if (result) 
					return;
			}
		}

		@Override
		public void visitRepeated(RepeatedTripleExpression expr, Object[] arguments) {
			if (expr.getCardinality().min == 0) {
				result = true;
			} else {
				expr.getSubExpression().accept(this, arguments);
			}
		}

		@Override
		public void visitTripleExprReference(TripleExprRef expr, Object... arguments) {
			expr.getTripleExp().accept(this, arguments);
		}
	}
}
