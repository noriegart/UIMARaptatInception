/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.kb.querybuilder;

import static de.tudarmstadt.ukp.inception.kb.IriConstants.FTS_FUSEKI;
import static de.tudarmstadt.ukp.inception.kb.IriConstants.FTS_LUCENE;
import static de.tudarmstadt.ukp.inception.kb.IriConstants.FTS_NONE;
import static de.tudarmstadt.ukp.inception.kb.IriConstants.FTS_VIRTUOSO;
import static de.tudarmstadt.ukp.inception.kb.IriConstants.FTS_WIKIDATA;
import static de.tudarmstadt.ukp.inception.kb.IriConstants.hasImplicitNamespace;
import static de.tudarmstadt.ukp.inception.kb.querybuilder.RdfCollection.collectionOf;
import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilder.Priority.PRIMARY;
import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilder.Priority.PRIMARY_RESTRICTIONS;
import static de.tudarmstadt.ukp.inception.kb.querybuilder.SPARQLQueryBuilder.Priority.SECONDARY;
import static java.lang.Character.isWhitespace;
import static java.lang.Integer.toHexString;
import static java.lang.System.currentTimeMillis;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Stream.concat;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions.and;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions.function;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions.notEquals;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions.or;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction.CONTAINS;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction.LANG;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction.LANGMATCHES;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction.REGEX;
import static org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction.STRSTARTS;
import static org.eclipse.rdf4j.sparqlbuilder.core.PropertyPaths.oneOrMore;
import static org.eclipse.rdf4j.sparqlbuilder.core.PropertyPaths.path;
import static org.eclipse.rdf4j.sparqlbuilder.core.PropertyPaths.zeroOrMore;
import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.prefix;
import static org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder.var;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.filterExists;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.filterNotExists;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.optional;
import static org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns.union;
import static org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.bNode;
import static org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.iri;
import static org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOf;
import static org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf.literalOfLanguage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.vocabulary.OWL;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.query.Binding;
import org.eclipse.rdf4j.query.BindingSet;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResult;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expression;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Operand;
import org.eclipse.rdf4j.sparqlbuilder.constraint.SparqlFunction;
import org.eclipse.rdf4j.sparqlbuilder.core.Prefix;
import org.eclipse.rdf4j.sparqlbuilder.core.Projectable;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfBlankNode.LabeledBlankNode;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfPredicate;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfValue;
import org.eclipse.rdf4j.sparqlbuilder.util.SparqlBuilderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.inception.kb.graph.KBHandle;
import de.tudarmstadt.ukp.inception.kb.graph.KBObject;
import de.tudarmstadt.ukp.inception.kb.model.KnowledgeBase;

/**
 * Build queries against the KB.
 * 
 * <p>
 * <b>Handling of subclasses: </b> Queries for subclasses return only resources which declare being
 * a class via the class property defined in the KB specification. This means that if the KB is
 * configured to use rdfs:Class but a subclass defines itself using owl:Class, then this subclass is
 * *not* returned. We do presently *not* support mixed schemes in a single KB.
 * </p>
 */
public class SPARQLQueryBuilder
    implements SPARQLQuery, SPARQLQueryPrimaryConditions, SPARQLQueryOptionalElements
{
    private final static Logger LOG = LoggerFactory.getLogger(SPARQLQueryBuilder.class);

    public static final int DEFAULT_LIMIT = 0;

    public static final String VAR_SUBJECT_NAME = "subj";
    public static final String VAR_PREDICATE_NAME = "pred";
    public static final String VAR_OBJECT_NAME = "obj";
    public static final String VAR_MATCH_TERM_PROPERTY_NAME = "pMatch";
    public static final String VAR_PREF_LABEL_PROPERTY_NAME = "pPrefLabel";
    public static final String VAR_PREF_LABEL_NAME = "l";
    public static final String VAR_MATCH_TERM_NAME = "m";
    public static final String VAR_SCORE_NAME = "sc";
    public static final String VAR_DESCRIPTION_NAME = "d";
    public static final String VAR_DESCRIPTION_CANDIDATE_NAME = "dc";
    public static final String VAR_RANGE_NAME = "range";
    public static final String VAR_DOMAIN_NAME = "domain";

    public static final Variable VAR_SUBJECT = var(VAR_SUBJECT_NAME);
    public static final Variable VAR_SCORE = var(VAR_SCORE_NAME);
    public static final Variable VAR_PREDICATE = var(VAR_PREDICATE_NAME);
    public static final Variable VAR_OBJECT = var(VAR_OBJECT_NAME);
    public static final Variable VAR_RANGE = var(VAR_RANGE_NAME);
    public static final Variable VAR_DOMAIN = var(VAR_DOMAIN_NAME);
    public static final Variable VAR_PREF_LABEL = var(VAR_PREF_LABEL_NAME);
    public static final Variable VAR_MATCH_TERM = var(VAR_MATCH_TERM_NAME);
    public static final Variable VAR_PREF_LABEL_PROPERTY = var(VAR_PREF_LABEL_PROPERTY_NAME);
    public static final Variable VAR_MATCH_TERM_PROPERTY = var(VAR_MATCH_TERM_PROPERTY_NAME);
    public static final Variable VAR_DESCRIPTION = var(VAR_DESCRIPTION_NAME);
    public static final Variable VAR_DESC_CANDIDATE = var(VAR_DESCRIPTION_CANDIDATE_NAME);

    public static final Prefix PREFIX_LUCENE_SEARCH = prefix("search",
            iri("http://www.openrdf.org/contrib/lucenesail#"));
    public static final Iri LUCENE_QUERY = PREFIX_LUCENE_SEARCH.iri("query");
    public static final Iri LUCENE_PROPERTY = PREFIX_LUCENE_SEARCH.iri("property");
    public static final Iri LUCENE_SCORE = PREFIX_LUCENE_SEARCH.iri("score");
    public static final Iri LUCENE_SNIPPET = PREFIX_LUCENE_SEARCH.iri("snippet");

    public static final Prefix PREFIX_FUSEKI_SEARCH = prefix("text",
            iri("http://jena.apache.org/text#"));
    public static final Iri FUSEKI_QUERY = PREFIX_FUSEKI_SEARCH.iri("query");

    public static final Iri OWL_INTERSECTIONOF = iri(OWL.INTERSECTIONOF.stringValue());
    public static final Iri RDF_REST = iri(RDF.REST.stringValue());
    public static final Iri RDF_FIRST = iri(RDF.FIRST.stringValue());

    private static final RdfValue EMPTY_STRING = () -> "\"\"";

    private final Set<Prefix> prefixes = new LinkedHashSet<>();
    private final Set<Projectable> projections = new LinkedHashSet<>();
    private final List<GraphPattern> primaryPatterns = new ArrayList<>();
    private final List<GraphPattern> primaryRestrictions = new ArrayList<>();
    private final List<GraphPattern> secondaryPatterns = new ArrayList<>();

    private boolean labelImplicitlyRetrieved = false;

    enum Priority
    {
        PRIMARY, PRIMARY_RESTRICTIONS, SECONDARY
    }

    /**
     * This flag is set internally to indicate whether the query should be skipped and an empty
     * result should always be returned. This can be the case, e.g. if the post-processing of the
     * query string against which to match a label causes the query to become empty.
     */
    private boolean returnEmptyResult = false;

    private final KnowledgeBase kb;
    private final Mode mode;

    /**
     * Case-insensitive mode is a best-effort approach. Depending on the underlying FTS, it may or
     * may not work.
     */
    private boolean caseInsensitive = true;

    private int limitOverride = DEFAULT_LIMIT;

    private boolean includeInferred = true;

    private boolean forceDisableFTS = false;

    /**
     * This flag controls whether we attempt to drop duplicate labels and descriptions on the side
     * of the SPARQL server (true) or whether we try retrieving all labels and descriptions which
     * have either no language or match the KB language and then drop duplicates on our side. Both
     * approaches have benefits and draw-backs. In general, we try server-side reduction to reduce
     * the data being transferred across the wire. However, in some cases we implicitly turn off
     * server side reduction because (SSR) we have not been able to figure out working SSR queries.
     * 
     * Benefits of SSR:
     * <ul>
     * <li>Less data to transfer across the wire</li>
     * <li>LIMIT works accurately (if we drop client side, we may end up with less than LIMIT
     * results)</li>
     * </ul>
     * 
     * Drawbacks of SSR:
     * <ul>
     * <li>More complex queries</li>
     * </ul>
     * 
     * @see #reduceRedundantResults(List)
     */

    private static enum Mode
    {
        ITEM, CLASS, INSTANCE, PROPERTY;

        protected Iri getLabelProperty(KnowledgeBase aKb)
        {
            return iri(getLabelPropertyAsString(aKb));
        }

        protected String getLabelPropertyAsString(KnowledgeBase aKb)
        {
            switch (this) {
            case ITEM: // fall-through
            case CLASS: // fall-through
            case INSTANCE:
                return aKb.getLabelIri();
            case PROPERTY:
                return aKb.getPropertyLabelIri();
            default:
                throw new IllegalStateException("Unsupported mode: " + this);
            }
        }

        protected Iri getDescriptionProperty(KnowledgeBase aKb)
        {
            switch (this) {
            case ITEM: // fall-through
            case CLASS: // fall-through
            case INSTANCE:
                return iri(aKb.getDescriptionIri());
            case PROPERTY:
                return iri(aKb.getPropertyDescriptionIri());
            default:
                throw new IllegalStateException("Unsupported mode: " + this);
            }
        }

        protected List<Iri> getAdditionalMatchingProperties(KnowledgeBase aKb)
        {
            switch (this) {
            case ITEM: // fall-through
            case CLASS: // fall-through
            case INSTANCE:
                return aKb.getAdditionalMatchingProperties().stream() //
                        .map(Rdf::iri) //
                        .collect(toList());
            case PROPERTY:
                return emptyList();
            default:
                throw new IllegalStateException("Unsupported mode: " + this);
            }
        }

        /**
         * @see SPARQLQueryPrimaryConditions#descendantsOf(String)
         */
        protected GraphPattern descendentsPattern(KnowledgeBase aKB, Iri aContext)
        {
            Iri typeOfProperty = iri(aKB.getTypeIri());
            Iri subClassProperty = iri(aKB.getSubclassIri());
            Iri subPropertyProperty = iri(aKB.getSubPropertyIri());

            switch (this) {
            case ITEM: {
                List<GraphPattern> classPatterns = new ArrayList<>();
                classPatterns.add(VAR_SUBJECT.has(path(oneOrMore(subClassProperty)), aContext));
                classPatterns.add(VAR_SUBJECT
                        .has(path(typeOfProperty, zeroOrMore(subClassProperty)), aContext));
                if (OWL.CLASS.stringValue().equals(aKB.getClassIri())) {
                    classPatterns.add(VAR_SUBJECT.has(
                            path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST), aContext));
                }

                return GraphPatterns.union(classPatterns.stream().toArray(GraphPattern[]::new));
            }
            case CLASS: {
                List<GraphPattern> classPatterns = new ArrayList<>();
                classPatterns.add(VAR_SUBJECT.has(path(oneOrMore(subClassProperty)), aContext));
                if (OWL.CLASS.stringValue().equals(aKB.getClassIri())) {
                    classPatterns.add(VAR_SUBJECT.has(
                            path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST), aContext));
                }

                return GraphPatterns.union(classPatterns.stream().toArray(GraphPattern[]::new));
            }
            case INSTANCE:
                return VAR_SUBJECT.has(path(typeOfProperty, zeroOrMore(subClassProperty)),
                        aContext);
            case PROPERTY:
                return VAR_SUBJECT.has(path(oneOrMore(subPropertyProperty)), aContext);
            default:
                throw new IllegalStateException("Unsupported mode: " + this);
            }
        }

        /**
         * @see SPARQLQueryPrimaryConditions#ancestorsOf(String)
         */
        protected GraphPattern ancestorsPattern(KnowledgeBase aKB, Iri aContext)
        {
            Iri typeOfProperty = iri(aKB.getTypeIri());
            Iri subClassProperty = iri(aKB.getSubclassIri());
            Iri subPropertyProperty = iri(aKB.getSubPropertyIri());

            switch (this) {
            case ITEM:
            case CLASS:
            case INSTANCE: {
                List<GraphPattern> classPatterns = new ArrayList<>();
                classPatterns.add(aContext.has(path(oneOrMore(subClassProperty)), VAR_SUBJECT));
                classPatterns.add(aContext.has(path(typeOfProperty, zeroOrMore(subClassProperty)),
                        VAR_SUBJECT));
                if (OWL.CLASS.stringValue().equals(aKB.getClassIri())) {
                    classPatterns.add(
                            aContext.has(path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST),
                                    VAR_SUBJECT));
                }

                return union(classPatterns.stream().toArray(GraphPattern[]::new));
            }
            case PROPERTY:
                return aContext.has(path(oneOrMore(subPropertyProperty)), VAR_SUBJECT);
            default:
                throw new IllegalStateException("Unsupported mode: " + this);
            }
        }

        /**
         * @see SPARQLQueryPrimaryConditions#childrenOf(String)
         */
        protected GraphPattern childrenPattern(KnowledgeBase aKB, Iri aContext)
        {
            Iri subPropertyProperty = iri(aKB.getSubPropertyIri());
            Iri subClassProperty = iri(aKB.getSubclassIri());
            Iri typeOfProperty = iri(aKB.getTypeIri());

            switch (this) {
            case ITEM: {
                List<GraphPattern> classPatterns = new ArrayList<>();
                classPatterns
                        .add(VAR_SUBJECT.has(() -> subClassProperty.getQueryString(), aContext));
                classPatterns.add(VAR_SUBJECT.has(typeOfProperty, aContext));
                if (OWL.CLASS.stringValue().equals(aKB.getClassIri())) {
                    classPatterns.add(VAR_SUBJECT.has(
                            path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST), aContext));
                }

                return GraphPatterns.union(classPatterns.stream().toArray(GraphPattern[]::new));
            }
            case INSTANCE: {
                return VAR_SUBJECT.has(typeOfProperty, aContext);
            }
            case CLASS: {
                // Follow the subclass property and also take into account owl:intersectionOf if
                // using OWL classes
                List<GraphPattern> classPatterns = new ArrayList<>();
                classPatterns.add(VAR_SUBJECT.has(subClassProperty, aContext));
                if (OWL.CLASS.stringValue().equals(aKB.getClassIri())) {
                    classPatterns.add(VAR_SUBJECT.has(
                            path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST), aContext));
                }

                return union(classPatterns.stream().toArray(GraphPattern[]::new));
            }
            case PROPERTY:
                return VAR_SUBJECT.has(subPropertyProperty, aContext);
            default:
                throw new IllegalStateException("Can only request children of classes");
            }
        }

        /**
         * @see SPARQLQueryPrimaryConditions#parentsOf(String)
         */
        protected GraphPattern parentsPattern(KnowledgeBase aKB, Iri aContext)
        {
            Iri subClassProperty = iri(aKB.getSubclassIri());
            Iri subPropertyProperty = iri(aKB.getSubPropertyIri());
            Iri typeOfProperty = iri(aKB.getTypeIri());

            switch (this) {
            case CLASS: {
                List<GraphPattern> classPatterns = new ArrayList<>();
                classPatterns.add(aContext.has(subClassProperty, VAR_SUBJECT));
                classPatterns.add(aContext.has(typeOfProperty, VAR_SUBJECT));
                if (OWL.CLASS.stringValue().equals(aKB.getClassIri())) {
                    classPatterns.add(
                            aContext.has(path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST),
                                    VAR_SUBJECT));
                }

                return union(classPatterns.stream().toArray(GraphPattern[]::new));
            }
            case PROPERTY:
                return aContext.has(path(oneOrMore(subPropertyProperty)), VAR_SUBJECT);
            default:
                throw new IllegalStateException(
                        "Can only request classes or properties as parents");
            }
        }

        /**
         * @see SPARQLQueryPrimaryConditions#roots()
         */
        protected GraphPattern rootsPattern(KnowledgeBase aKb)
        {
            Iri classIri = iri(aKb.getClassIri());
            Iri subClassProperty = iri(aKb.getSubclassIri());
            Iri typeOfProperty = iri(aKb.getTypeIri());
            Variable otherSubclass = var("otherSubclass");

            switch (this) {
            case CLASS: {
                List<GraphPattern> rootPatterns = new ArrayList<>();

                Set<String> rootConcepts = aKb.getRootConcepts();
                if (rootConcepts != null && !rootConcepts.isEmpty()) {
                    rootPatterns.add(new ValuesPattern(VAR_SUBJECT, rootConcepts.stream()
                            .map(iri -> iri(iri)).collect(Collectors.toList())));
                }
                else {
                    List<GraphPattern> classPatterns = new ArrayList<>();
                    classPatterns.add(VAR_SUBJECT.has(subClassProperty, otherSubclass)
                            .filter(notEquals(VAR_SUBJECT, otherSubclass)));
                    if (OWL.CLASS.stringValue().equals(aKb.getClassIri())) {
                        classPatterns.add(VAR_SUBJECT.has(OWL_INTERSECTIONOF, bNode()));
                    }

                    rootPatterns.add(union(
                            // ... it is explicitly defined as being a class
                            VAR_SUBJECT.has(typeOfProperty, classIri),
                            // ... it is used as the type of some instance
                            // This can be a very slow condition - so we have to skip it
                            // bNode().has(typeOfProperty, VAR_SUBJECT),
                            // ... it has any subclass
                            bNode().has(subClassProperty, VAR_SUBJECT)).filterNotExists(
                                    union(classPatterns.stream().toArray(GraphPattern[]::new))));
                }

                return GraphPatterns
                        .and(rootPatterns.toArray(new GraphPattern[rootPatterns.size()]));
            }
            default:
                throw new IllegalStateException("Can only query for root classes");
            }
        }
    }

    /**
     * Retrieve any item from the KB. There is no check if the item looks like a class, instance or
     * property. The IRI and property mapping used in the patters is obtained from the given KB
     * configuration.
     */
    public static SPARQLQueryPrimaryConditions forItems(KnowledgeBase aKB)
    {
        return new SPARQLQueryBuilder(aKB, Mode.ITEM);
    }

    /**
     * Retrieve only things that look like classes. Identifiers for classes participate as ID in
     * {@code ID IS-A CLASS-IRI}, {@code X SUBCLASS-OF ID}, {@code ID SUBCLASS-OF X}. The IRI and
     * property mapping used in the patters is obtained from the given KB configuration.
     */
    public static SPARQLQueryPrimaryConditions forClasses(KnowledgeBase aKB)
    {
        SPARQLQueryBuilder builder = new SPARQLQueryBuilder(aKB, Mode.CLASS);
        builder.limitToClasses();
        return builder;
    }

    /**
     * Retrieve instances. Instances do <b>not</b> look like classes. The IRI and property mapping
     * used in the patters is obtained from the given KB configuration.
     */
    public static SPARQLQueryPrimaryConditions forInstances(KnowledgeBase aKB)
    {
        SPARQLQueryBuilder builder = new SPARQLQueryBuilder(aKB, Mode.INSTANCE);
        builder.limitToInstances();
        return builder;
    }

    /**
     * Retrieve properties. The IRI and property mapping used in the patters is obtained from the
     * given KB configuration.
     */
    public static SPARQLQueryPrimaryConditions forProperties(KnowledgeBase aKB)
    {
        SPARQLQueryBuilder builder = new SPARQLQueryBuilder(aKB, Mode.PROPERTY);
        builder.limitToProperties();
        return builder;
    }

    private SPARQLQueryBuilder(KnowledgeBase aKB, Mode aMode)
    {
        kb = aKB;
        mode = aMode;

        // The Wikidata search service we are using does not return properties, so we have to do
        // this the old-fashioned way...
        if (Mode.PROPERTY.equals(mode)
                && FTS_WIKIDATA.stringValue().equals(aKB.getFullTextSearchIri())) {
            forceDisableFTS = true;
        }
    }

    private void addPattern(Priority aPriority, GraphPattern aPattern)
    {
        switch (aPriority) {
        case PRIMARY:
            primaryPatterns.add(aPattern);
            break;
        case PRIMARY_RESTRICTIONS:
            primaryRestrictions.add(aPattern);
            break;
        case SECONDARY:
            secondaryPatterns.add(aPattern);
            break;
        default:
            throw new IllegalArgumentException("Unknown priority: [" + aPriority + "]");
        }
    }

    private void addMatchTermProjections(Collection<Projectable> aProjectables)
    {
        aProjectables.add(VAR_MATCH_TERM);
    }

    private void addPreferredLabelProjections(Collection<Projectable> aProjectables)
    {
        aProjectables.add(VAR_PREF_LABEL);
    }

    private Projectable getDescriptionProjection()
    {
        return VAR_DESC_CANDIDATE;
    }

    @Override
    public SPARQLQueryOptionalElements includeInferred()
    {
        includeInferred(true);

        return this;
    }

    @Override
    public SPARQLQueryOptionalElements excludeInferred()
    {
        includeInferred(false);

        return this;
    }

    @Override
    public SPARQLQueryOptionalElements includeInferred(boolean aEnabled)
    {
        includeInferred = aEnabled;

        return this;
    }

    @Override
    public SPARQLQueryOptionalElements limit(int aLimit)
    {
        limitOverride = aLimit;
        return this;
    }

    @Override
    public SPARQLQueryOptionalElements caseSensitive()
    {
        caseInsensitive = false;
        return this;
    }

    @Override
    public SPARQLQueryOptionalElements caseSensitive(boolean aEnabled)
    {
        caseInsensitive = !aEnabled;
        return this;
    }

    @Override
    public SPARQLQueryOptionalElements caseInsensitive()
    {
        caseInsensitive = true;
        return this;
    }

    /**
     * Generates a pattern which binds all sub-properties of the label property to the given
     * variable.
     */
    private GraphPattern bindPrefLabelProperties(Variable aVariable)
    {
        Iri pLabel = mode.getLabelProperty(kb);
        Iri pSubProperty = iri(kb.getSubPropertyIri());
        var primaryLabelPattern = aVariable.has(path(zeroOrMore(pSubProperty)), pLabel);
        return optional(primaryLabelPattern);
    }

    /**
     * Generates a pattern which binds all sub-properties of the matching properties to the given
     * variable.
     */
    private GraphPattern bindMatchTermProperties(Variable aVariable)
    {
        Iri pLabel = mode.getLabelProperty(kb);
        Iri pSubProperty = iri(kb.getSubPropertyIri());
        var primaryLabelPattern = aVariable.has(path(zeroOrMore(pSubProperty)), pLabel);

        if (mode.getAdditionalMatchingProperties(kb).isEmpty()) {
            // If we only have a single label property, let's make the label optional
            // so that we also get a result for things that might potentially not have
            // any label at all.
            return optional(primaryLabelPattern);
        }

        var patterns = new ArrayList<TriplePattern>();
        patterns.add(primaryLabelPattern);

        for (Iri pAddSearch : mode.getAdditionalMatchingProperties(kb)) {
            patterns.add(aVariable.has(path(zeroOrMore(pSubProperty)), pAddSearch));
        }

        // If additional label properties are specified, then having a label candidate
        // becomes mandatory, otherwise we get one result for every label property and
        // additional label property and their sub-properties for every concept and that
        // is simply too much.
        return GraphPatterns.union(patterns.stream().toArray(TriplePattern[]::new));
    }

    @Override
    public SPARQLQueryPrimaryConditions withIdentifier(String... aIdentifiers)
    {
        forceDisableFTS = true;

        addPattern(PRIMARY, new ValuesPattern(VAR_SUBJECT,
                Arrays.stream(aIdentifiers).map(Rdf::iri).toArray(RdfValue[]::new)));

        return this;
    }

    @Override
    public SPARQLQueryPrimaryConditions matchingDomain(String aIdentifier)
    {
        forceDisableFTS = true;

        // The original code considered owl:unionOf in the domain definition... we do not do this
        // at the moment, but to see how it was before and potentially restore that behavior, we
        // keep a copy of the old query here.
        // return String.join("\n"
        // , SPARQL_PREFIX
        // , "SELECT DISTINCT ?s ?l ((?labelGeneral) AS ?lGen) WHERE {"
        // , "{ ?s rdfs:domain/(owl:unionOf/rdf:rest*/rdf:first)* ?aDomain }"
        // , " UNION "
        // , "{ ?s a ?prop "
        // , " VALUES ?prop { rdf:Property owl:ObjectProperty owl:DatatypeProperty
        // owl:AnnotationProperty} "
        // , " FILTER NOT EXISTS { ?s rdfs:domain/(owl:unionOf/rdf:rest*/rdf:first)* ?x } }"
        // , optionalLanguageFilteredValue("?pLABEL", aKB.getDefaultLanguage(),"?s","?l")
        // , optionalLanguageFilteredValue("?pLABEL", null,"?s","?labelGeneral")
        // , queryForOptionalSubPropertyLabel(labelProperties, aKB.getDefaultLanguage(),"?s","?spl")
        // , "}"
        // , "LIMIT " + aKB.getMaxResults());

        Iri subClassProperty = iri(kb.getSubclassIri());
        Iri subPropertyProperty = iri(kb.getSubPropertyIri());
        LabeledBlankNode superClass = Rdf.bNode("superClass");

        addPattern(PRIMARY, union(GraphPatterns.and(
                // Find all super-classes of the domain type
                iri(aIdentifier).has(path(zeroOrMore(subClassProperty)), superClass),
                // Either there is a domain which matches the given one
                VAR_SUBJECT.has(iri(RDFS.DOMAIN), superClass)),
                // ... the property does not define or inherit domain
                isPropertyPattern().and(filterNotExists(VAR_SUBJECT
                        .has(path(zeroOrMore(subPropertyProperty), iri(RDFS.DOMAIN)), bNode())))));

        return this;
    }

    @Override
    public SPARQLQueryBuilder withLabelMatchingExactlyAnyOf(String... aValues)
    {
        String[] values = Arrays.stream(aValues) //
                .map(SPARQLQueryBuilder::trimQueryString) //
                .filter(StringUtils::isNotBlank) //
                .toArray(String[]::new);

        if (values.length == 0) {
            returnEmptyResult = true;
            return this;
        }

        IRI ftsMode = getFtsMode();

        if (FTS_LUCENE.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingExactlyAnyOf_RDF4J_FTS(values));
        }
        else if (FTS_FUSEKI.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingExactlyAnyOf_Fuseki_FTS(values));
        }
        else if (FTS_VIRTUOSO.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingExactlyAnyOf_Virtuoso_FTS(values));
        }
        else if (FTS_WIKIDATA.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingExactlyAnyOf_Wikidata_FTS(values));
        }
        else if (FTS_NONE.equals(ftsMode) || ftsMode == null) {
            // For exact matching, we do not make use of regexes, so we keep this as a primary
            // condition - unlike in withLabelContainingAnyOf or withLabelStartingWith
            addPattern(PRIMARY, withLabelMatchingExactlyAnyOf_No_FTS(values));
        }
        else {
            throw new IllegalStateException(
                    "Unknown FTS mode: [" + kb.getFullTextSearchIri() + "]");
        }

        addMatchTermProjections(projections);
        labelImplicitlyRetrieved = true;

        return this;
    }

    private IRI getFtsMode()
    {
        if (forceDisableFTS || kb.getFullTextSearchIri() == null) {
            return FTS_NONE;
        }

        return SimpleValueFactory.getInstance().createIRI(kb.getFullTextSearchIri());
    }

    private GraphPattern withLabelMatchingExactlyAnyOf_No_FTS(String[] aValues)
    {
        List<RdfValue> values = new ArrayList<>();
        String language = kb.getDefaultLanguage();

        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_noFTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            if (language != null) {
                values.add(literalOfLanguage(sanitizedValue, language));
            }

            values.add(literalOf(sanitizedValue));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                new ValuesPattern(VAR_MATCH_TERM, values),
                VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM));
    }

    private GraphPattern withLabelMatchingExactlyAnyOf_RDF4J_FTS(String[] aValues)
    {
        prefixes.add(PREFIX_LUCENE_SEARCH);

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            // Strip single quotes and asterisks because they have special semantics
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT
                    .has(FTS_LUCENE,
                            bNode(LUCENE_QUERY, literalOf(sanitizedValue)).andHas(LUCENE_PROPERTY,
                                    VAR_MATCH_TERM_PROPERTY))
                    .andHas(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                    .filter(equalsPattern(VAR_MATCH_TERM, value, kb)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelMatchingExactlyAnyOf_Fuseki_FTS(String[] aValues)
    {
        prefixes.add(PREFIX_FUSEKI_SEARCH);

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            // We assume that the FTS is case insensitive and found that some FTSes (i.e.
            // Fuseki) can have trouble matching if they get upper-case query when they
            // internally lower-case#
            if (caseInsensitive) {
                String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";
                sanitizedValue = sanitizedValue.toLowerCase(Locale.forLanguageTag(language));
            }

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns
                    .add(collectionOf(VAR_SUBJECT, VAR_SCORE, VAR_MATCH_TERM)
                            .has(FUSEKI_QUERY,
                                    collectionOf(VAR_MATCH_TERM_PROPERTY,
                                            literalOf(sanitizedValue)))
                            .filter(equalsPattern(VAR_MATCH_TERM, value, kb)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelMatchingExactlyAnyOf_Virtuoso_FTS(String[] aValues)
    {
        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                    .and(VAR_MATCH_TERM.has(FTS_VIRTUOSO, literalOf("\"" + sanitizedValue + "\"")))
                    .filter(equalsPattern(VAR_MATCH_TERM, value, kb)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelMatchingExactlyAnyOf_Wikidata_FTS(String[] aValues)
    {
        // In our KB settings, the language can be unset, but the Wikidata entity search
        // requires a preferred language. So we use English as the default.
        String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(new WikidataEntitySearchService(VAR_SUBJECT, sanitizedValue, language)
                    .and(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                            .filter(equalsPattern(VAR_MATCH_TERM, value, kb))));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    @Override
    public SPARQLQueryBuilder withLabelMatchingAnyOf(String... aValues)
    {
        String[] values = Arrays.stream(aValues).map(SPARQLQueryBuilder::trimQueryString)
                .filter(StringUtils::isNotBlank).toArray(String[]::new);

        if (values.length == 0) {
            returnEmptyResult = true;
            return this;
        }

        IRI ftsMode = getFtsMode();

        if (FTS_LUCENE.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingAnyOf_RDF4J_FTS(values));
        }
        else if (FTS_FUSEKI.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingAnyOf_Fuseki_FTS(values));
        }
        else if (FTS_VIRTUOSO.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingAnyOf_Virtuoso_FTS(values));
        }
        else if (FTS_WIKIDATA.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelMatchingAnyOf_Wikidata_FTS(values));
        }
        else if (FTS_NONE.equals(ftsMode) || ftsMode == null) {
            // The WikiData search service does not support properties. So we disable the use of the
            // WikiData search service when looking for properties. But then, searching first by
            // the label becomes very slow because withLabelMatchingAnyOf falls back to "containing"
            // when no FTS is used. To avoid forcing the SPARQL server to perform a full scan
            // of its database, we demote the label matching to a secondary condition, allowing the
            // the matching by type (e.g. PRIMARY_RESTRICTIONS is-a property) to take precedence.
            addPattern(SECONDARY, withLabelMatchingAnyOf_No_FTS(values));
        }
        else {
            throw new IllegalStateException(
                    "Unknown FTS mode: [" + kb.getFullTextSearchIri() + "]");
        }

        addMatchTermProjections(projections);
        labelImplicitlyRetrieved = true;

        return this;
    }

    private GraphPattern withLabelMatchingAnyOf_No_FTS(String[] aValues)
    {
        // Falling back to "contains" semantics if there is no FTS
        return withLabelContainingAnyOf_No_FTS(aValues);
    }

    private GraphPattern withLabelMatchingAnyOf_Wikidata_FTS(String[] aValues)
    {
        // In our KB settings, the language can be unset, but the Wikidata entity search
        // requires a preferred language. So we use English as the default.
        String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(new WikidataEntitySearchService(VAR_SUBJECT, sanitizedValue, language)
                    .and(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelMatchingAnyOf_Virtuoso_FTS(String[] aValues)
    {
        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM).and(
                    VAR_MATCH_TERM.has(FTS_VIRTUOSO, literalOf("\"" + sanitizedValue + "\""))));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelMatchingAnyOf_Fuseki_FTS(String[] aValues)
    {
        prefixes.add(PREFIX_FUSEKI_SEARCH);

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);
            String fuzzyQuery = convertToFuzzyMatchingQuery(sanitizedValue);

            if (StringUtils.isBlank(sanitizedValue) || StringUtils.isBlank(fuzzyQuery)) {
                continue;
            }

            // We assume that the FTS is case insensitive and found that some FTSes (i.e.
            // Fuseki) can have trouble matching if they get upper-case query when they
            // internally lower-case#
            if (caseInsensitive) {
                String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";
                sanitizedValue = sanitizedValue.toLowerCase(Locale.forLanguageTag(language));
            }

            valuePatterns.add(collectionOf(VAR_SUBJECT, VAR_SCORE, VAR_MATCH_TERM).has(FUSEKI_QUERY,
                    collectionOf(VAR_MATCH_TERM_PROPERTY, literalOf(fuzzyQuery))));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelMatchingAnyOf_RDF4J_FTS(String[] aValues)
    {
        prefixes.add(PREFIX_LUCENE_SEARCH);

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            // Strip single quotes and asterisks because they have special semantics
            String sanitizedValue = sanitizeQueryString_FTS(value);

            String fuzzyQuery = convertToFuzzyMatchingQuery(sanitizedValue);

            if (StringUtils.isBlank(sanitizedValue) || StringUtils.isBlank(fuzzyQuery)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT
                    .has(FTS_LUCENE,
                            bNode(LUCENE_QUERY, literalOf(fuzzyQuery)).andHas(LUCENE_PROPERTY,
                                    VAR_MATCH_TERM_PROPERTY))
                    .andHas(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    @Override
    public SPARQLQueryBuilder withLabelContainingAnyOf(String... aValues)
    {
        String[] values = Arrays.stream(aValues).map(SPARQLQueryBuilder::trimQueryString)
                .filter(StringUtils::isNotBlank).toArray(String[]::new);

        if (values.length == 0) {
            returnEmptyResult = true;
            return this;
        }

        IRI ftsMode = getFtsMode();

        if (FTS_LUCENE.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelContainingAnyOf_RDF4J_FTS(values));
        }
        else if (FTS_FUSEKI.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelContainingAnyOf_Fuseki_FTS(values));
        }
        else if (FTS_VIRTUOSO.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelContainingAnyOf_Virtuoso_FTS(values));
        }
        else if (FTS_WIKIDATA.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelContainingAnyOf_Wikidata_FTS(values));
        }
        else if (FTS_NONE.equals(ftsMode) || ftsMode == null) {
            // Label matching without FTS is slow, so we add this with low prio and hope that some
            // other higher-prio condition exists which limites the number of candidates to a
            // manageable level
            addPattern(SECONDARY, withLabelContainingAnyOf_No_FTS(values));
        }
        else {
            throw new IllegalStateException(
                    "Unknown FTS mode: [" + kb.getFullTextSearchIri() + "]");
        }

        addMatchTermProjections(projections);
        labelImplicitlyRetrieved = true;

        return this;
    }

    private GraphPattern withLabelContainingAnyOf_No_FTS(String... aValues)
    {
        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            if (StringUtils.isBlank(value)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                    .filter(containsPattern(VAR_MATCH_TERM, value)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelContainingAnyOf_RDF4J_FTS(String[] aValues)
    {
        prefixes.add(PREFIX_LUCENE_SEARCH);

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT
                    .has(FTS_LUCENE,
                            bNode(LUCENE_QUERY, literalOf(sanitizedValue + "*"))
                                    .andHas(LUCENE_PROPERTY, VAR_MATCH_TERM_PROPERTY))
                    .andHas(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                    .filter(containsPattern(VAR_MATCH_TERM, value)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelContainingAnyOf_Fuseki_FTS(String[] aValues)
    {
        prefixes.add(PREFIX_FUSEKI_SEARCH);

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            // We assume that the FTS is case insensitive and found that some FTSes (i.e.
            // Fuseki) can have trouble matching if they get upper-case query when they
            // internally lower-case#
            if (caseInsensitive) {
                String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";
                sanitizedValue = sanitizedValue.toLowerCase(Locale.forLanguageTag(language));
            }

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns
                    .add(collectionOf(VAR_SUBJECT, VAR_SCORE, VAR_MATCH_TERM)
                            .has(FUSEKI_QUERY,
                                    collectionOf(VAR_MATCH_TERM_PROPERTY,
                                            literalOf(sanitizedValue)))
                            .filter(containsPattern(VAR_MATCH_TERM, value)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelContainingAnyOf_Virtuoso_FTS(String[] aValues)
    {
        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                    .and(VAR_MATCH_TERM.has(FTS_VIRTUOSO, literalOf("\"" + sanitizedValue + "\"")))
                    .filter(containsPattern(VAR_MATCH_TERM, value)));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    private GraphPattern withLabelContainingAnyOf_Wikidata_FTS(String[] aValues)
    {
        // In our KB settings, the language can be unset, but the Wikidata entity search
        // requires a preferred language. So we use English as the default.
        String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";

        List<GraphPattern> valuePatterns = new ArrayList<>();
        for (String value : aValues) {
            String sanitizedValue = sanitizeQueryString_FTS(value);

            if (StringUtils.isBlank(sanitizedValue)) {
                continue;
            }

            valuePatterns.add(new WikidataEntitySearchService(VAR_SUBJECT, sanitizedValue, language)
                    .and(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                            .filter(containsPattern(VAR_MATCH_TERM, value))));
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                union(valuePatterns.toArray(new GraphPattern[valuePatterns.size()])));
    }

    @Override
    public SPARQLQueryBuilder withLabelStartingWith(String aPrefixQuery)
    {
        String value = trimQueryString(aPrefixQuery);

        if (value == null || value.length() == 0) {
            returnEmptyResult = true;
            return this;
        }

        IRI ftsMode = getFtsMode();

        if (FTS_LUCENE.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelStartingWith_RDF4J_FTS(value));
        }
        else if (FTS_FUSEKI.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelStartingWith_Fuseki_FTS(value));
        }
        else if (FTS_VIRTUOSO.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelStartingWith_Virtuoso_FTS(value));
        }
        else if (FTS_WIKIDATA.equals(ftsMode)) {
            addPattern(PRIMARY, withLabelStartingWith_Wikidata_FTS(value));
        }
        else if (FTS_NONE.equals(ftsMode) || ftsMode == null) {
            // Label matching without FTS is slow, so we add this with low prio and hope that some
            // other higher-prio condition exists which limites the number of candidates to a
            // manageable level
            addPattern(SECONDARY, withLabelStartingWith_No_FTS(value));
        }
        else {
            throw new IllegalStateException(
                    "Unknown FTS mode: [" + kb.getFullTextSearchIri() + "]");
        }

        addMatchTermProjections(projections);
        labelImplicitlyRetrieved = true;

        return this;
    }

    private GraphPattern withLabelStartingWith_No_FTS(String aPrefixQuery)
    {
        if (aPrefixQuery.isEmpty()) {
            returnEmptyResult = true;
        }

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                        .filter(startsWithPattern(VAR_MATCH_TERM, aPrefixQuery)));
    }

    private GraphPattern withLabelStartingWith_Wikidata_FTS(String aPrefix)
    {
        // In our KB settings, the language can be unset, but the Wikidata entity search
        // requires a preferred language. So we use English as the default.
        String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";

        if (aPrefix.isEmpty()) {
            returnEmptyResult = true;
        }

        String sanitizedValue = sanitizeQueryString_FTS(aPrefix);

        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                new WikidataEntitySearchService(VAR_SUBJECT, sanitizedValue, language)
                        .and(VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                                .filter(startsWithPattern(VAR_MATCH_TERM, aPrefix))));
    }

    private GraphPattern withLabelStartingWith_Virtuoso_FTS(String aPrefixQuery)
    {
        StringBuilder ftsQueryString = new StringBuilder();
        ftsQueryString.append("\"");

        // Strip single quotes and asterisks because they have special semantics
        String sanitizedQuery = sanitizeQueryString_FTS(aPrefixQuery);

        // If the query string entered by the user does not end with a space character, then
        // we assume that the user may not yet have finished writing the word and add a
        // wildcard
        if (!aPrefixQuery.endsWith(" ")) {
            String[] queryTokens = sanitizedQuery.split(" ");
            for (int i = 0; i < queryTokens.length; i++) {
                if (i > 0) {
                    ftsQueryString.append(" ");
                }

                // Virtuoso requires that a token has at least 4 characters before it can be
                // used with a wildcard. If the last token has less than 4 characters, we simply
                // drop it to avoid the user hitting a point where the auto-suggesions suddenly
                // are empty. If the token 4 or more, we add the wildcard.
                if (i == (queryTokens.length - 1)) {
                    if (queryTokens[i].length() >= 4) {
                        ftsQueryString.append(queryTokens[i]);
                        ftsQueryString.append("*");
                    }
                }
                else {
                    ftsQueryString.append(queryTokens[i]);
                }
            }
        }
        else {
            ftsQueryString.append(sanitizedQuery);
        }

        ftsQueryString.append("\"");

        // If the query string was reduced to nothing, then the query should always return an empty
        // result.
        if (ftsQueryString.length() == 2) {
            returnEmptyResult = true;
        }

        // Locate all entries where the label contains the prefix (using the FTS) and then
        // filter them by those which actually start with the prefix.
        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                VAR_SUBJECT.has(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                        .and(VAR_MATCH_TERM.has(FTS_VIRTUOSO, literalOf(ftsQueryString.toString())))
                        .filter(startsWithPattern(VAR_MATCH_TERM, aPrefixQuery)));
    }

    private GraphPattern withLabelStartingWith_RDF4J_FTS(String aPrefixQuery)
    {
        prefixes.add(PREFIX_LUCENE_SEARCH);

        // Strip single quotes and asterisks because they have special semantics
        String sanitizedValue = sanitizeQueryString_FTS(aPrefixQuery);

        if (StringUtils.isBlank(sanitizedValue)) {
            returnEmptyResult = true;
        }

        String queryString = sanitizedValue.trim();

        // If the query string entered by the user does not end with a space character, then
        // we assume that the user may not yet have finished writing the word and add a
        // wildcard
        if (!aPrefixQuery.endsWith(" ")) {
            queryString += "*";
        }

        // Locate all entries where the label contains the prefix (using the FTS) and then
        // filter them by those which actually start with the prefix.
        return GraphPatterns.and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                VAR_SUBJECT
                        .has(FTS_LUCENE,
                                bNode(LUCENE_QUERY, literalOf(queryString)).andHas(LUCENE_PROPERTY,
                                        VAR_MATCH_TERM_PROPERTY))
                        .andHas(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM)
                        .filter(startsWithPattern(VAR_MATCH_TERM, aPrefixQuery)));
    }

    private GraphPattern withLabelStartingWith_Fuseki_FTS(String aPrefixQuery)
    {
        prefixes.add(PREFIX_FUSEKI_SEARCH);

        String queryString = aPrefixQuery.trim();

        // We assume that the FTS is case insensitive and found that some FTSes (i.e.
        // Fuseki) can have trouble matching if they get upper-case query when they
        // internally lower-case#
        if (caseInsensitive) {
            String language = kb.getDefaultLanguage() != null ? kb.getDefaultLanguage() : "en";
            queryString = queryString.toLowerCase(Locale.forLanguageTag(language));
        }

        if (queryString.isEmpty()) {
            returnEmptyResult = true;
        }

        // If the query string entered by the user does not end with a space character, then
        // we assume that the user may not yet have finished writing the word and add a
        // wildcard
        if (!aPrefixQuery.endsWith(" ")) {
            queryString += "*";
        }

        // Locate all entries where the label contains the prefix (using the FTS) and then
        // filter them by those which actually start with the prefix.
        return GraphPatterns
                .and(bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY),
                        collectionOf(VAR_SUBJECT, VAR_SCORE, VAR_MATCH_TERM)
                                .has(FUSEKI_QUERY,
                                        collectionOf(VAR_MATCH_TERM_PROPERTY,
                                                literalOf(queryString)))
                                .filter(startsWithPattern(VAR_MATCH_TERM, aPrefixQuery)));
    }

    private Expression<?> startsWithPattern(Variable aVariable, String aPrefixQuery)
    {
        return matchString(STRSTARTS, aVariable, aPrefixQuery);
    }

    private Expression<?> containsPattern(Variable aVariable, String aSubstring)
    {
        return matchString(CONTAINS, aVariable, aSubstring);
    }

    private String asRegexp(String aValue)
    {
        String value = aValue;
        // Escape metacharacters
        // value = value.replaceAll("[{}()\\[\\].+*?^$\\\\|]", "\\\\\\\\$0");
        value = value.replaceAll("[{}()\\[\\].+*?^$\\\\|]+", ".+");
        // Replace consecutive whitespace or control chars with a whitespace matcher
        value = value.replaceAll("[\\p{Space}\\p{Cntrl}]+", "\\\\\\\\s+");
        return value;
    }

    private Expression<?> equalsPattern(Variable aVariable, String aValue, KnowledgeBase aKB)
    {
        String language = aKB.getDefaultLanguage();

        List<Expression<?>> expressions = new ArrayList<>();

        Operand variable = aVariable;

        String regexFlags = "";
        if (caseInsensitive) {
            regexFlags += "i";
        }

        // Match using REGEX to be resilient against extra whitespace
        // Match exactly
        String value = "^" + asRegexp(aValue) + "$";

        // Match with default language
        if (language != null) {
            expressions.add(and(function(REGEX, variable, literalOf(value), literalOf(regexFlags)),
                    function(LANGMATCHES, function(LANG, aVariable), literalOf(language)))
                            .parenthesize());

            // Match without language
            expressions.add(and(function(REGEX, variable, literalOf(value), literalOf(regexFlags)),
                    function(LANGMATCHES, function(LANG, aVariable), EMPTY_STRING)).parenthesize());
        }
        else {
            // Match ignoring language
            expressions.add(function(REGEX, variable, literalOf(value), literalOf(regexFlags)));
        }

        return or(expressions.toArray(new Expression<?>[expressions.size()]));
    }

    private Expression<?> matchString(SparqlFunction aFunction, Variable aVariable, String aValue)
    {
        String language = kb.getDefaultLanguage();

        List<Expression<?>> expressions = new ArrayList<>();

        Operand variable = aVariable;

        String regexFlags = "";
        if (caseInsensitive) {
            regexFlags += "i";
        }

        String value;
        switch (aFunction) {
        // Match using REGEX to be resilient against extra whitespace
        case STRSTARTS:
            // Match at start
            value = "^" + asRegexp(aValue);
            break;
        case CONTAINS:
            // Match anywhere
            value = ".*" + asRegexp(aValue) + ".*";
            break;
        default:
            throw new IllegalArgumentException(
                    "Only STRSTARTS and CONTAINS are supported, but got [" + aFunction + "]");
        }

        if (language != null) {
            // Match with default language
            expressions.add(and(function(REGEX, variable, literalOf(value), literalOf(regexFlags)),
                    function(LANGMATCHES, function(LANG, aVariable), literalOf(language)))
                            .parenthesize());

            // Match without language
            expressions.add(and(function(REGEX, variable, literalOf(value), literalOf(regexFlags)),
                    function(LANGMATCHES, function(LANG, aVariable), EMPTY_STRING)).parenthesize());
        }
        else {
            // Match ignoring language
            expressions.add(function(REGEX, variable, literalOf(value), literalOf(regexFlags)));
        }

        // Match with any language (the reduce code doesn't handle this properly atm)
        // expressions.add(function(aFunction, variable, literalOf(value)));

        return or(expressions.toArray(new Expression<?>[expressions.size()]));
    }

    @Override
    public SPARQLQueryPrimaryConditions roots()
    {
        forceDisableFTS = true;

        addPattern(PRIMARY, mode.rootsPattern(kb));

        return this;
    }

    @Override
    public SPARQLQueryPrimaryConditions ancestorsOf(String aItemIri)
    {
        forceDisableFTS = true;

        Iri contextIri = iri(aItemIri);

        addPattern(PRIMARY, mode.ancestorsPattern(kb, contextIri));

        return this;
    }

    @Override
    public SPARQLQueryPrimaryConditions descendantsOf(String aClassIri)
    {
        forceDisableFTS = true;

        Iri contextIri = iri(aClassIri);

        addPattern(PRIMARY, mode.descendentsPattern(kb, contextIri));

        return this;
    }

    @Override
    public SPARQLQueryPrimaryConditions childrenOf(String aClassIri)
    {
        forceDisableFTS = true;

        Iri contextIri = iri(aClassIri);

        addPattern(PRIMARY, mode.childrenPattern(kb, contextIri));

        return this;
    }

    @Override
    public SPARQLQueryPrimaryConditions parentsOf(String aClassIri)
    {
        forceDisableFTS = true;

        Iri contextIri = iri(aClassIri);

        addPattern(PRIMARY, mode.parentsPattern(kb, contextIri));

        return this;
    }

    private void limitToClasses()
    {
        Iri classIri = iri(kb.getClassIri());
        Iri subClassProperty = iri(kb.getSubclassIri());
        Iri typeOfProperty = iri(kb.getTypeIri());

        List<GraphPattern> classPatterns = new ArrayList<>();

        // An item is a class if ...
        // ... it is explicitly defined as being a class
        classPatterns.add(VAR_SUBJECT.has(typeOfProperty, classIri));
        // ... it has any subclass
        classPatterns.add(bNode().has(subClassProperty, VAR_SUBJECT));
        // ... it has any superclass
        classPatterns.add(VAR_SUBJECT.has(subClassProperty, bNode()));
        // ... it is used as the type of some instance
        // This can be a very slow condition - so we have to skip it
        // classPatterns.add(bNode().has(typeOfProperty, VAR_SUBJECT));
        // ... it participates in an owl:intersectionOf
        if (OWL.CLASS.stringValue().equals(kb.getClassIri())) {
            classPatterns.add(VAR_SUBJECT
                    .has(path(OWL_INTERSECTIONOF, zeroOrMore(RDF_REST), RDF_FIRST), bNode()));
        }

        addPattern(PRIMARY_RESTRICTIONS,
                filterExists(union(classPatterns.stream().toArray(GraphPattern[]::new))));
    }

    private void limitToInstances()
    {
        Iri classIri = iri(kb.getClassIri());
        Iri subClassProperty = iri(kb.getSubclassIri());
        Iri typeOfProperty = iri(kb.getTypeIri());

        // An item is an instance if ...
        addPattern(PRIMARY_RESTRICTIONS, filterExists(VAR_SUBJECT.has(typeOfProperty, bNode()))
                // ... it is not explicitly defined as being a class
                .filterNotExists(VAR_SUBJECT.has(typeOfProperty, classIri))
                // ... it does not have any subclass
                .filterNotExists(bNode().has(subClassProperty, VAR_SUBJECT))
                // ... it does not have any superclass
                .filterNotExists(VAR_SUBJECT.has(subClassProperty, bNode())));
    }

    private void limitToProperties()
    {
        addPattern(PRIMARY_RESTRICTIONS, filterExists(isPropertyPattern()));
    }

    private GraphPattern isPropertyPattern()
    {
        Iri propertyIri = iri(kb.getPropertyTypeIri());
        Iri subPropertyProperty = iri(kb.getSubPropertyIri());
        Iri typeOfProperty = iri(kb.getTypeIri());
        Iri pSubClass = iri(kb.getSubclassIri());

        List<GraphPattern> propertyPatterns = new ArrayList<>();

        // An item is a property if ...
        // ... it is explicitly defined as being a property
        propertyPatterns
                .add(VAR_SUBJECT.has(path(typeOfProperty, zeroOrMore(pSubClass)), propertyIri));
        // ... it has any subproperties
        propertyPatterns.add(bNode().has(subPropertyProperty, VAR_SUBJECT));
        // ... it has any superproperties
        propertyPatterns.add(VAR_SUBJECT.has(subPropertyProperty, bNode()));

        // This may be a bit too general... e.g. it takes forever to complete on YAGO
        //// ... or it essentially appears in the predicate position :)
        // propertyPatterns.add(bNode().has(VAR_SUBJECT, bNode()));

        return union(propertyPatterns.stream().toArray(GraphPattern[]::new));
    }

    @Override
    public SPARQLQueryOptionalElements retrieveLabel()
    {
        if (!mode.getAdditionalMatchingProperties(kb).isEmpty()) {
            addPreferredLabelProjections(projections);
            addPattern(SECONDARY, bindPrefLabelProperties(VAR_PREF_LABEL_PROPERTY));
            retrieveOptional(VAR_PREF_LABEL_PROPERTY, VAR_PREF_LABEL);
        }

        // If the label is already retrieved, do nothing
        if (labelImplicitlyRetrieved) {
            return this;
        }

        addMatchTermProjections(projections);
        addPattern(SECONDARY, bindMatchTermProperties(VAR_MATCH_TERM_PROPERTY));
        retrieveOptional(VAR_MATCH_TERM_PROPERTY, VAR_MATCH_TERM);

        return this;
    }

    @Override
    public SPARQLQueryOptionalElements retrieveDescription()
    {
        // Retain only the first description
        projections.add(getDescriptionProjection());

        Iri descProperty = mode.getDescriptionProperty(kb);
        retrieveOptional(descProperty, VAR_DESC_CANDIDATE);

        return this;
    }

    private void retrieveOptional(RdfPredicate aProperty, Variable aVariable)
    {
        String language = kb.getDefaultLanguage();

        List<GraphPattern> patterns = new ArrayList<>();

        // Find all labels corresponding to the KB language
        if (language != null) {
            // Find all labels without any language
            patterns.add(VAR_SUBJECT.has(aProperty, aVariable)
                    .filter(function(LANGMATCHES, function(LANG, aVariable), EMPTY_STRING)));

            patterns.add(VAR_SUBJECT.has(aProperty, aVariable)
                    .filter(function(LANGMATCHES, function(LANG, aVariable), literalOf(language))));
        }
        else {
            // Find all labels ignoring any language
            patterns.add(VAR_SUBJECT.has(aProperty, aVariable));
        }

        // Virtuoso has trouble with multiple OPTIONAL clauses causing results which would
        // normally match to be removed from the results set. Using a UNION seems to address this
        // labelPatterns.forEach(pattern -> addPattern(Priority.SECONDARY, optional(pattern)));
        addPattern(SECONDARY, optional(union(patterns.toArray(new GraphPattern[patterns.size()]))));
    }

    @Override
    public SPARQLQueryOptionalElements retrieveDomainAndRange()
    {
        projections.add(VAR_RANGE);
        projections.add(VAR_DOMAIN);

        addPattern(SECONDARY, optional(VAR_SUBJECT.has(RDFS.RANGE, VAR_RANGE)));
        addPattern(SECONDARY, optional(VAR_SUBJECT.has(RDFS.DOMAIN, VAR_DOMAIN)));

        return this;
    }

    private int getLimit()
    {
        return limitOverride > 0 ? limitOverride : kb.getMaxResults();
    }

    @Override
    public SelectQuery selectQuery()
    {
        // Must add it anyway because we group by it
        projections.add(VAR_SUBJECT);

        SelectQuery query = Queries.SELECT().distinct();
        prefixes.forEach(query::prefix);
        projections.forEach(query::select);

        // First add the primary patterns and high-level restrictions (e.g. limits to classes or
        // instances) - this is important because Virtuoso has trouble when combining UNIONS,
        // property paths FILTERS and OPTIONALS (which we do a lot). It seems to help when we put
        // the FILTERS together with the primary part of the query into a group.
        // See: https://github.com/openlink/virtuoso-opensource/issues/831
        query.where(() -> SparqlBuilderUtils.getBracedString(
                GraphPatterns.and(concat(primaryPatterns.stream(), primaryRestrictions.stream())
                        .toArray(GraphPattern[]::new)).getQueryString()));

        // Then add the optional or lower-prio elements
        secondaryPatterns.stream().forEach(query::where);

        if (kb.getDefaultDatasetIri() != null) {
            query.from(SparqlBuilder.dataset(SparqlBuilder.from(iri(kb.getDefaultDatasetIri()))));
        }

        int actualLimit = getLimit();

        // If we do not do a server-side reduce, then we may get two results for every item
        // from the server (one with and one without the language), so we need to double the
        // query limit and cut down results locally later.
        actualLimit = actualLimit * 2;

        query.limit(actualLimit);

        return query;
    }

    @Override
    public List<KBHandle> asHandles(RepositoryConnection aConnection, boolean aAll)
    {
        long startTime = currentTimeMillis();
        String queryId = toHexString(hashCode());

        String queryString = selectQuery().getQueryString();
        // queryString = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, queryString, null)
        // .toString();
        LOG.trace("[{}] Query: {}", queryId, queryString);

        List<KBHandle> results;
        if (returnEmptyResult) {
            LOG.debug("[{}] Query was skipped because it would not return any results anyway",
                    queryId);

            return emptyList();
        }

        try {
            TupleQuery tupleQuery = aConnection.prepareTupleQuery(queryString);
            tupleQuery.setIncludeInferred(includeInferred);
            results = evaluateListQuery(tupleQuery, aAll);
            results.sort(Comparator.comparing(KBObject::getUiLabel, String.CASE_INSENSITIVE_ORDER));

            LOG.debug("[{}] Query returned {} results in {}ms", queryId, results.size(),
                    currentTimeMillis() - startTime);
            return results;
        }
        catch (QueryEvaluationException e) {
            throw new QueryEvaluationException(
                    e.getMessage() + " while running query:\n" + queryString, e);
        }
    }

    /**
     * Execute the query and return {@code true} if the result set is not empty. This internally
     * limits the number of results requested via SPARQL to 1 and should complete faster than
     * retrieving the entire results set and checking whether it is empty.
     * 
     * @param aConnection
     *            a connection to a triple store.
     * @param aAll
     *            True if entities with implicit namespaces (e.g. defined by RDF)
     * @return {@code true} if the result set is not empty.
     */
    @Override
    public boolean exists(RepositoryConnection aConnection, boolean aAll)
    {
        long startTime = currentTimeMillis();
        String queryId = toHexString(hashCode());

        limit(1);

        SelectQuery query = selectQuery();

        String queryString = query.getQueryString();
        LOG.trace("[{}] Query: {}", queryId, queryString);

        if (returnEmptyResult) {
            LOG.debug("[{}] Query was skipped because it would not return any results anyway",
                    queryId);

            return false;
        }

        try {
            TupleQuery tupleQuery = aConnection.prepareTupleQuery(queryString);
            boolean result = !evaluateListQuery(tupleQuery, aAll).isEmpty();

            LOG.debug("[{}] Query returned {} in {}ms", queryId, result,
                    currentTimeMillis() - startTime);

            return result;
        }
        catch (QueryEvaluationException e) {
            throw new QueryEvaluationException(
                    e.getMessage() + " while running query:\n" + queryString, e);
        }
    }

    @Override
    public Optional<KBHandle> asHandle(RepositoryConnection aConnection, boolean aAll)
    {
        long startTime = currentTimeMillis();
        String queryId = toHexString(hashCode());

        limit(1);

        String queryString = selectQuery().getQueryString();
        LOG.trace("[{}] Query: {}", queryId, queryString);

        Optional<KBHandle> result;
        if (returnEmptyResult) {
            LOG.debug("[{}] Query was skipped because it would not return any results anyway",
                    queryId);
            return Optional.empty();
        }

        try {
            TupleQuery tupleQuery = aConnection.prepareTupleQuery(queryString);
            tupleQuery.setIncludeInferred(includeInferred);
            result = evaluateListQuery(tupleQuery, aAll).stream().findFirst();

            LOG.debug("[{}] Query returned a result in {}ms", queryId,
                    currentTimeMillis() - startTime);
            return result;
        }
        catch (QueryEvaluationException e) {
            throw new QueryEvaluationException(
                    e.getMessage() + " while running query:\n" + queryString, e);
        }
    }

    /**
     * Method process the Tuple Query Results
     * 
     * @param tupleQuery
     *            Tuple Query Variable
     * @param aAll
     *            True if entities with implicit namespaces (e.g. defined by RDF)
     * @return list of all the {@link KBHandle}
     */
    private List<KBHandle> evaluateListQuery(TupleQuery tupleQuery, boolean aAll)
        throws QueryEvaluationException
    {
        try (TupleQueryResult result = tupleQuery.evaluate()) {
            List<KBHandle> handles = new ArrayList<>();
            while (result.hasNext()) {
                BindingSet bindings = result.next();
                if (bindings.size() == 0) {
                    continue;
                }

                // LOG.trace("[{}] Bindings: {}", toHexString(hashCode()), bindings);

                String id = bindings.getBinding(VAR_SUBJECT_NAME).getValue().stringValue();
                if (!id.contains(":") || (!aAll && hasImplicitNamespace(kb, id))) {
                    continue;
                }

                KBHandle handle = new KBHandle(id);
                handle.setKB(kb);

                extractLabel(handle, bindings);
                extractDescription(handle, bindings);
                extractRange(handle, bindings);
                extractDomain(handle, bindings);

                handles.add(handle);
            }

            return reduceRedundantResults(handles);
        }
    }

    /**
     * Make sure that each result is only represented once, preferably in the default language.
     */
    private List<KBHandle> reduceRedundantResults(List<KBHandle> aHandles)
    {
        Map<String, KBHandle> cMap = new LinkedHashMap<>();
        for (KBHandle handle : aHandles) {
            KBHandle current = cMap.get(handle.getIdentifier());

            // Not recorded yet -> add it
            if (current == null) {
                cMap.put(handle.getIdentifier(), handle);
            }
            // Found one with a label while current one doesn't have one
            else if (current.getName() == null && handle.getName() != null) {
                cMap.put(handle.getIdentifier(), handle);
            }
            // Found an exact language match -> use that one instead
            // Note that having a language implies that there is a label!
            else if (kb.getDefaultLanguage() != null
                    && kb.getDefaultLanguage().equals(handle.getLanguage())) {
                cMap.put(handle.getIdentifier(), handle);
            }
        }

        LOG.trace("Input: {}", aHandles);
        LOG.trace("Output: {}", cMap.values());

        return cMap.values().stream().limit(getLimit()).collect(Collectors.toList());
    }

    private void extractLabel(KBHandle aTargetHandle, BindingSet aSourceBindings)
    {
        Binding prefLabel = aSourceBindings.getBinding(VAR_PREF_LABEL_NAME);
        Binding matchTerm = aSourceBindings.getBinding(VAR_MATCH_TERM_NAME);

        // Obtain name either from the pref-label or from the match term if available
        if (prefLabel != null) {
            aTargetHandle.setName(prefLabel.getValue().stringValue());
            extractLanguage(prefLabel).ifPresent(aTargetHandle::setLanguage);
        }
        else if (matchTerm != null) {
            aTargetHandle.setName(matchTerm.getValue().stringValue());
            extractLanguage(matchTerm).ifPresent(aTargetHandle::setLanguage);
        }

        // If we have additional search properties, we need to store the label candidates
        // in the handle as well
        if (!mode.getAdditionalMatchingProperties(kb).isEmpty() && matchTerm != null) {
            String language = extractLanguage(matchTerm).orElse(null);
            aTargetHandle.addMatchTerm(matchTerm.getValue().stringValue(), language);
        }

        // Binding subPropertyLabel = aSourceBindings.getBinding("spl");
        // if (subPropertyLabel != null) {
        // aTargetHandle.setName(subPropertyLabel.getValue().stringValue());
        // if (subPropertyLabel.getValue() instanceof Literal) {
        // Literal literal = (Literal) subPropertyLabel.getValue();
        // literal.getLanguage().ifPresent(aTargetHandle::setLanguage);
        // }
        // }
    }

    private Optional<String> extractLanguage(Binding aBinding)
    {
        if (aBinding != null && aBinding.getValue() instanceof Literal) {
            Literal literal = (Literal) aBinding.getValue();
            return literal.getLanguage();
        }

        return Optional.empty();
    }

    private void extractDescription(KBHandle aTargetHandle, BindingSet aSourceBindings)
    {
        Binding description = aSourceBindings.getBinding(VAR_DESCRIPTION_NAME);
        Binding descCandidate = aSourceBindings.getBinding(VAR_DESCRIPTION_CANDIDATE_NAME);
        if (description != null) {
            aTargetHandle.setDescription(description.getValue().stringValue());
        }
        else if (descCandidate != null) {
            aTargetHandle.setDescription(descCandidate.getValue().stringValue());
        }
    }

    private void extractDomain(KBHandle aTargetHandle, BindingSet aSourceBindings)
    {
        Binding domain = aSourceBindings.getBinding(VAR_DOMAIN_NAME);
        if (domain != null) {
            aTargetHandle.setDomain(domain.getValue().stringValue());
        }
    }

    private void extractRange(KBHandle aTargetHandle, BindingSet aSourceBindings)
    {
        Binding range = aSourceBindings.getBinding(VAR_RANGE_NAME);
        if (range != null) {
            aTargetHandle.setRange(range.getValue().stringValue());
        }
    }

    /**
     * Removes leading and trailing space and single quote characters which could cause the query
     * string to escape its quotes in the SPARQL query.
     */
    public static String trimQueryString(String aQuery)
    {
        if (aQuery == null || aQuery.length() == 0) {
            return aQuery;
        }

        boolean trailingSpace = isWhitespace(aQuery.charAt(aQuery.length() - 1));

        int end = aQuery.length();
        while (end > 0 && (isWhitespace(aQuery.charAt(end - 1)) || aQuery.charAt(end - 1) == '\''
                || aQuery.charAt(end - 1) == '"')) {
            end--;
        }

        int begin = 0;
        while (begin < aQuery.length() && (isWhitespace(aQuery.charAt(begin))
                || aQuery.charAt(begin) == '\'' || aQuery.charAt(begin) == '"')) {
            begin++;
        }

        if (begin >= end) {
            return "";
        }

        if (begin > 0 || end < aQuery.length()) {
            if (trailingSpace) {
                return aQuery.substring(begin, end) + ' ';
            }
            else {
                return aQuery.substring(begin, end);
            }
        }

        return aQuery;
    }

    public static String sanitizeQueryString_noFTS(String aQuery)
    {
        return aQuery
                // character classes to replace with a simple space
                .replaceAll("[\\p{Space}\\p{Cntrl}]+", " ") //
                .trim();
    }

    public static String sanitizeQueryString_FTS(String aQuery)
    {
        return aQuery
                // character classes to replace with a simple space
                .replaceAll("[\\p{Punct}\\p{Space}\\p{Cntrl}[~+*(){}\\[\\]]]+", " ")
                // character classes to remove from the query string
                // \u00AD : SOFT HYPHEN
                .replaceAll("[\\u00AD]", "").trim();
    }

    public static String convertToFuzzyMatchingQuery(String aQuery)
    {
        StringJoiner joiner = new StringJoiner(" ");
        for (String term : aQuery.split(" ")) {
            if (term.length() > 4) {
                joiner.add(term + "~");
            }
            // REC: excluding terms of 3 or less characters helps reducing the problem that a
            // mention of "Counties of Catherlagh" matches "Anne of Austria", but actually
            // this should be handled by stopwords and not be excluding any short words...
            // I think
            else if (term.length() >= 3) {
                joiner.add(term);
            }
        }

        return joiner.toString();
    }

    @Override
    public boolean equals(final Object other)
    {
        if (!(other instanceof SPARQLQueryBuilder)) {
            return false;
        }

        SPARQLQueryBuilder castOther = (SPARQLQueryBuilder) other;
        String query = selectQuery().getQueryString();
        String otherQuery = castOther.selectQuery().getQueryString();
        return query.equals(otherQuery);
    }

    @Override
    public int hashCode()
    {
        return selectQuery().getQueryString().hashCode();
    }
}
