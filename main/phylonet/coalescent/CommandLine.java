package phylonet.coalescent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import phylonet.tree.io.NewickReader;
import phylonet.tree.io.ParseException;
import phylonet.tree.model.MutableTree;
import phylonet.tree.model.Tree;
import phylonet.tree.model.sti.STITree;
import phylonet.tree.util.Trees;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import com.martiansoftware.jsap.Parameter;
import com.martiansoftware.jsap.SimpleJSAP;
import com.martiansoftware.jsap.Switch;
import com.martiansoftware.jsap.stringparsers.FileStringParser;

public class CommandLine {

    protected static String _versinon = "4.10.3";


    private static void exitWithErr(String extraMessage, SimpleJSAP jsap) {
        System.err.println();
        System.err.println(extraMessage);
        System.err.println();
        System.err.println("Usage: java -jar astral."+_versinon+".jar "+ jsap.getUsage());
        System.err.println();
        System.err.println(jsap.getHelp());
        System.exit( 1 );
    }


    private static SimpleJSAP getJSAP() throws JSAPException {
        return new SimpleJSAP(
                "ASTRAL (version" + _versinon + ")",
                "species tree inference from unrooted gene trees. "
                + "The ASTRAL algorithm maximizes the number of shared quartet trees with"
                + " the collection of all gene trees. The result of this optimization problem"
                + " is statistically consistent under the multi-species coalesent model."
                + " This software can also solve MGD and MGDL problems (see options) instead of ASTRAL.",
                    
                new Parameter[] {
                    
                    new FlaggedOption("input file", 
                            FileStringParser.getParser().setMustExist(true), null, JSAP.REQUIRED, 
                            'i', "input",
                            "a file containing input gene trees in newick format. (required)"),
                            
                    new FlaggedOption( "output file",
                            FileStringParser.getParser(), null, JSAP.NOT_REQUIRED,
                            'o', "output",
                            "a filename for storing the output species tree. Defaults to outputting to stdout."),

                    new FlaggedOption("score species trees", 
                            FileStringParser.getParser().setMustExist(true), null, JSAP.NOT_REQUIRED, 
                            'q', "score-tree",
                            "score the provided species tree and exit"),
                    
                    new FlaggedOption("branch annotation level", 
                    		JSAP.INTEGER_PARSER, "3", JSAP.NOT_REQUIRED,
                            't', "branch-annotate",
                            "How much annotations should be added to each branch: 0, 1, or 2. \n"
                            + "0: no annotations. \n"
                            + "1: only the quartet support for the main resolution. \n"
                            + "2: full annotation (quartet support, quartet frequency, and posterior probability for all three alternatives, "
                               + "plus total number of quartets around the branch and effective number of genes).\n"
                            + "3 (default): only the posterior probability for the main resolution.\n"
                            + "4: only three alternative posterior probabilities."),
                            
	                new FlaggedOption("bootstraps", 
	                        FileStringParser.getParser().setMustExist(true), null, JSAP.NOT_REQUIRED,
	                        'b', "bootstraps",
	                        "perform multi-locus bootstrapping using input bootstrap replicate files (use --rep to change the number of replications). "
	                        + "The file given with this option should have a list of the gene tree bootstrap files, one per line, and each line corresponding to one gene. "
	                        + "By default performs site-only resampling, but gene/site resampling can also be used. "),
	
	                new FlaggedOption("replicates", 
	                        JSAP.INTEGER_PARSER, "100", JSAP.NOT_REQUIRED, 
	                        'r', "reps",
	                        "Set the number of bootstrap replicates done in multi-locus bootstrapping. "),
	
	                new FlaggedOption("seed", 
	                        JSAP.LONG_PARSER, "692", JSAP.NOT_REQUIRED,
	                        's', "seed",
	                        "Set the seed number used in multi-locus bootstrapping. "),
	                        
	                new Switch("gene-sampling",
	                        'g', "gene-resampling",
	                        "perform gene tree resampling in addition to site resampling. Useful only with the -b option."),

	                new FlaggedOption("keep", 
	                        JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED, 
	                        'k', "keep",
	                          " -k completed: outputs completed gene trees (i.e. after adding missing taxa) to a file called [output file name].completed_gene_trees.\n"
	                        + " -k bootstraps: outputs individual bootstrap replciates to a file called [output file name].[i].bs\n"
	                        + " -k bootstraps_norun: just like -k bootstraps, but exits after outputting bootstraps.\n"
	                        + " -k searchspace_norun: outputs the search space and exits; use searchspace to continue the run after outputting the search space."
	                        + "When -k option is used, -o option needs to be given. "
	                        + "The file name specified using -o is used as the prefix for the name of the extra output files.").setAllowMultipleDeclarations(true),

	                new FlaggedOption("lambda", 
	                        JSAP.DOUBLE_PARSER, "0.5", JSAP.NOT_REQUIRED,
	                        'c', "lambda",
	                        "Set the lambda parameter for the Yule prior used in the calculaitons"
	                        + " of branch lengths and posterior probabilities. Set to zero to get ML branch "
	                        + "lengths instead of MAP."
	                        + " Higher values tend to shorten estimated branch lengths and very"
	                        + " high values can give inaccurate results (or even result in underflow)."),

	                new FlaggedOption("mapping file", 
	                        FileStringParser.getParser().setMustExist(true), null, JSAP.NOT_REQUIRED, 
	                        'a', "namemapfile",
	                        "a file containing the mapping between names in gene tree and names in the species tree. "
	                        + "The mapping file has one line per species, with one of two formats:\n"
	                        + " species: gene1,gene2,gene3,gene4\n"
	                        + " species 4 gene1 gene2 gene3 gene4\n"),
	
	                new FlaggedOption("minleaves", 
	                        JSAP.INTEGER_PARSER, null, JSAP.NOT_REQUIRED, 
	                        'm', "minleaves",
	                        "Remove genes with less than specificed number of leaves "),
	
	                new Switch( "duplication",
	                        'd', "dup",
	                        "Solves MGD problem. Minimizes the number duplications required to explain "
	                        + "gene trees using DynaDup algorithm (Bayzid, 2011). Note that with this option, "
	                        + "DynaDyp would be used *instead of* ASTRAL."),
	                                            
                    new Switch("exact",
                            'x', "exact",
                            "find the exact solution by looking at all clusters - recommended only for small (<18) numer of taxa."),
   
                    new FlaggedOption("extraLevel",
                    		JSAP.INTEGER_PARSER, "1", JSAP.NOT_REQUIRED,
                            'p', "extraLevel",
                            "How much extra bipartitions should be added: 0, 1, or 2. "
                            + "0: adds nothing extra. "
                            + "1 (default): adds to X but not excessively (greedy resolutions). "
                            + "2: addes a potentially large number and therefore can be slow (quadratic distance-based)."),
   
                    new FlaggedOption("extra trees", 
                            FileStringParser.getParser().setMustExist(true), null, JSAP.NOT_REQUIRED, 
                            'e', "extra",
                            "provide extra trees (with gene labels) used to enrich the set of clusters searched"),

                    new FlaggedOption("extra species trees", 
                            FileStringParser.getParser().setMustExist(true), null, JSAP.NOT_REQUIRED, 
                            'f', "extra-species",
                            "provide extra trees (with species lables) used to enrich the set of clusters searched"),

                    new FlaggedOption( "duploss weight",
                            JSAP.STRING_PARSER, null, JSAP.NOT_REQUIRED,
                            'l', "duploss",
                            "Solves MGDL problem. Minimizes the number duplication and losses required"
                            + " to explain gene trees using DynaDup algorithm. Note that with this option, "
                            + "DynaDyp would be used *instead of* ASTRAL. "
                            + "Use -l 0 for standard (homomorphic) definition, and -l 1 for our new bd definition. "
                            + "Any value in between weights the impact of missing taxa somewhere between these two extremes. "
                            + "-l auto will automaticaly pick this weight. "), 
                });
    }


    public static void main(String[] args) throws Exception{
		
    	long startTime = System.currentTimeMillis();

        SimpleJSAP jsap;		
        JSAPResult config;
        int criterion = 2; // 2 for ASTRAL, 0 for dup, 1 for duploss
		boolean rooted = false;
		boolean extrarooted = false;		
		Map<String, String> taxonMap = null;
		String replace = null;
		String pattern = null;
		List<Tree> mainTrees;
		List<List<String>> bstrees = new ArrayList<List<String>>();
		List<List<String>> inputSets = new ArrayList<List<String>>();
		List<Tree> extraTrees = new ArrayList<Tree>();
		double wh = 1.0D;
		//int addExtra;
		int k = 0;
		//int annotate = 1;
		Integer minleaves = null;
        BufferedWriter outbuffer;
        Set<String> keepOptions = new HashSet<String>();
		
        jsap = getJSAP();     
        config = jsap.parse(args);  
        if ( jsap.messagePrinted() ) {
            exitWithErr("",jsap);
        }
        
        File outfile = config.getFile("output file");  
        if (outfile == null) {
            outbuffer = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
            outbuffer = new BufferedWriter(new FileWriter(outfile));
            GlobalMaps.outputfilename = config.getFile("output file").getCanonicalPath();
        }
        
        
        
        if (config.getBoolean("duplication") && config.contains("duploss weight")) {
            exitWithErr("dup and duploss options cannot be used together. Choose only one. ",jsap);
        }
        if (config.getBoolean("duplication")) {
            criterion = 0;
            rooted = true;
            extrarooted = true;
            System.err.println("Using DynaDup application, minimizing MGD (not ASTRAL).");
        }
        if (config.contains("duploss weight")) {
            criterion = 1;
            rooted = true;
            extrarooted = true;
            String v = config.getString("duploss weight");
            if (v.equals("auto")) {
                wh = -1;
            } else {
                wh = Double.parseDouble(v);
                if (wh < 0.0D || wh > 1.0D) {
                    exitWithErr("duploss weight has to be between 0 and 1",jsap);
                };
            }
            System.err.println("Using DynaDup application, minimizing MGDL (not ASTRAL).");
        }
        
        System.err.println("\n================== ASTRAL ===================== \n" );
        System.err.println("This is ASTRAL version " + _versinon);

        System.err.println("Gene trees are treated as " + (rooted ? "rooted" : "unrooted"));

        GlobalMaps.random = new Random(config.getLong("seed"));
        
        
        if (config.getFile("mapping file") != null) {

            BufferedReader br = new BufferedReader(new FileReader(
                    config.getFile("mapping file")));

            taxonMap = new HashMap<String, String>();
            String s;
            while ((s = br.readLine()) != null) {
                s = s.trim();
                String species;
                String[] alleles;
                if (s.indexOf(":") != -1) {
                    species = s.substring(0, s.indexOf(":")).trim();
                    s = s.substring(s.indexOf(":") + 1);
                    alleles = s.split(",");
                } else {
                    alleles = s.split(" ",3);
                    species = alleles[0];
                    alleles = alleles[2].split(" ");
                }
                for (String allele : alleles) {
                    allele = allele.trim();
                    if (taxonMap.containsKey(allele)) {
                        System.err
                        .println("The input file is not in correct format");
                        System.err
                        .println("Any gene name can only map to one species");
                        System.exit(-1);
                    } else {
                        //System.err.println("Mapping '"+allele+"' to '"+species+"'");
                        taxonMap.put(allele, species);
                    }
                }
            }
            br.close();
        }
        
        minleaves = config.contains("minleaves")? config.getInt("minleaves"):null;
        
        try {
        	
        	//GlobalMaps.taxonIdentifier.taxonId("0");

        	mainTrees = readInputTrees(
        			readTreeFileAsString(config.getFile("input file")),
        					rooted, true, false, minleaves, 
        					config.getInt("branch annotation level"));			
            k = mainTrees.size();
            System.err.println(k+" trees read from " + config.getFile("input file"));
            
            GlobalMaps.taxonIdentifier.lock();        

        } catch (IOException e) {
            System.err.println("Error when reading trees.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            return;
        } 
        
        if (mainTrees.size() == 0) {
            System.err.println("Empty list of trees. The function exits.");
            return;
        }
                
        if (taxonMap != null) {
            GlobalMaps.taxonNameMap = new TaxonNameMap(taxonMap);
        } else if (replace != null) {   
            GlobalMaps.taxonNameMap = new TaxonNameMap (pattern, replace);
        } else {
            GlobalMaps.taxonNameMap = new TaxonNameMap();
        }
        
        Options options = newOptions(criterion, rooted, extrarooted, 
        		1.0D, 1.0D, wh, keepOptions, config);
        
        /*Options bsoptions = newOptions(criterion, rooted, extrarooted, 
        		1.0D, 1.0D, wh, keepOptions, config);
        bsoptions.setBranchannotation(0);*/
       
        if (config.getFile("score species trees") != null) {
        	List<Tree> toScore = readInputTrees(
        			readTreeFileAsString(config.getFile("score species trees")),
                     rooted, true, true, null, 1);
        	
            AbstractInference inference =
                    initializeInference(criterion, mainTrees, extraTrees, options);
        	for (Tree tr : toScore) {
        		inference.scoreGeneTree(tr, true);
                writeTreeToFile(outbuffer, tr);
        	}
        	
        	outbuffer.close();
        	System.exit(0);
        }
        
        String outgroup = GlobalMaps.taxonNameMap.getSpeciesIdMapper().getSpeciesName(0);
        System.err.println("All outputted trees will be *arbitrarily* rooted at "+outgroup);
        
        if (config.getStringArray("keep") != null && config.getStringArray("keep").length != 0) {
        	if (GlobalMaps.outputfilename == null) {
        		throw new JSAPException("When -k option is used, -o is also needed.");
        	}
        	for (String koption : config.getStringArray("keep")) {
        		if ("completed".equals(koption) ||
        			"bootstraps".equals(koption) ||
        			"bootstraps_norun".equals(koption)||
        			"searchspace_norun".equals(koption)||
        			"searchspace".equals(koption)) {
        			keepOptions.add(koption);
        		} else {
        			throw new JSAPException("-k "+koption+" not recognized.");
        		}
        	}
        }
        
        try {

            if (config.getFile("extra trees") != null) {
            	extraTrees = readInputTrees(
                	readTreeFileAsString(config.getFile("extra trees")), 
                        extrarooted, true, false, null, 1);
                System.err.println(extraTrees.size() + " extra trees read from "
                        + config.getFile("extra trees"));
            }
            
            if (config.getFile("extra species trees") != null) {
            	extraTrees = readInputTrees(
                	readTreeFileAsString(config.getFile("extra species trees")), 
                        extrarooted, true, true, null, 1);
                System.err.println(extraTrees.size() + " extra trees read from "
                        + config.getFile("extra trees"));
            }
            
        } catch (IOException e) {
            System.err.println("Error when reading extra trees.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            return;
        } 
        
        try {           

            if (config.getFile("bootstraps") != null) {
                String line;
                BufferedReader rebuff = new BufferedReader(new FileReader(config.getFile("bootstraps")));
                while ((line = rebuff.readLine()) != null) {
                    List<String> g = readTreeFileAsString(new File(line));
                    Collections.shuffle(g, GlobalMaps.random);
                    bstrees.add(g);
                }
                rebuff.close();
            }

        } catch (IOException e) {
            System.err.println("Error when reading bootstrap trees.");
            System.err.println(e.getMessage());
            e.printStackTrace();
            return;
        } 

	
		if (config.getFile("bootstraps") != null) {
	        System.err.println("Bootstrapping with seed "+config.getLong("seed"));
		    for (int i = 0; i < config.getInt("replicates"); i++) {
		        List<String> input = new ArrayList<String>();
		        inputSets.add(input);   
		        try {
    		        if (config.getBoolean("gene-sampling")) {
    		            for (int j = 0; j < k; j++) {
                            input.add(bstrees.get(GlobalMaps.random.nextInt(k)).remove(0));                 
                        }
    		        } else {   		        
    		            for (List<String> gene : bstrees) {
    		                input.add(gene.get(i));
    		            }
    		        }
		        } catch (IndexOutOfBoundsException e) {
		            exitWithErr("Error: You seem to have asked for "+config.getInt("replicates")+
		                    " but only "+ i +" replicaes could be created.\n" + 
		                    " Note that for gene resampling, you need more input bootstrap" +
		                    " replicates than the number of species tee replicates.", jsap);
		        }
			    if (keepOptions.contains("bootstraps_norun") ||
				    	keepOptions.contains("bootstraps")) {
			    	String bsfn = outfile + ( "." + i + ".bs" );
				    BufferedWriter bsoutbuffer = new BufferedWriter(new FileWriter(bsfn));
				    for (String tree: input) {
				    	bsoutbuffer.write(tree + " \n");
				    }
				    bsoutbuffer.close();
				}
		    }
		    if (keepOptions.contains("bootstraps_norun") ||
			    	keepOptions.contains("bootstraps")) {
		    	System.err.println("bootstrap files written to files "+ outfile + ( "." + 0 + ".bs" ) + 
		    			" to "+outfile + ( "." + config.getInt("replicates") + ".bs" ));
		    }
		    if (keepOptions.contains("bootstraps_norun")) {
		    	System.err.println("Exiting after outputting the bootstrap files");
		    	System.exit(0);
		    }
		}

        int j = 0;
        List<Tree> bootstraps = new ArrayList<Tree>();
		for ( List<String> input : inputSets) {  
	        System.err.println("\n======== Running bootstrap replicate " + j++);
	        bootstraps.add(runOnOneInput(criterion, 
	                 extraTrees, outbuffer, 
                    readInputTrees(input, rooted, false, false, minleaves, config.getInt("branch annotation level")),
                    null, outgroup, options));
		}
	    
		if (bootstraps != null && bootstraps.size() != 0) {
            STITree<Double> cons = (STITree<Double>) Utils.greedyConsensus(bootstraps,false, GlobalMaps.taxonNameMap.getSpeciesIdMapper().getSTTaxonIdentifier());
            cons.rerootTreeAtNode(cons.getNode(outgroup));
			Trees.removeBinaryNodes(cons);
            Utils.computeEdgeSupports(cons, bootstraps);
            writeTreeToFile(outbuffer, cons);
		}
		
        System.err.println("\n======== Running the main analysis");
        runOnOneInput(criterion, extraTrees, outbuffer, mainTrees, bootstraps, 
                outgroup, options);
           
		outbuffer.close();
		
	    System.err.println("ASTRAL finished in "  + 
	            (System.currentTimeMillis() - startTime) / 1000.0D + " secs");
	}


    private static Tree runOnOneInput(int criterion, List<Tree> extraTrees,
    		BufferedWriter outbuffer, List<Tree> input, 
            Iterable<Tree> bootstraps, String outgroup, Options options) {
        long startTime;
        startTime = System.currentTimeMillis();
        
        AbstractInference inference =
            initializeInference(criterion, input, extraTrees, options);
        
        List<Solution> solutions = inference.inferSpeciesTree();
   
        System.err.println("Optimal tree inferred in "
        		+ (System.currentTimeMillis() - startTime) / 1000.0D + " secs");
        
        Tree st = solutions.get(0)._st;
        st.rerootTreeAtNode(st.getNode(outgroup));
		Trees.removeBinaryNodes((MutableTree) st);
   
		inference.scoreGeneTree(st, false);
		
        if ((bootstraps != null) && (bootstraps.iterator().hasNext())) {
            for (Solution solution : solutions) {
                Utils.computeEdgeSupports((STITree<Double>) solution._st, bootstraps);
            }
        }
        writeTreeToFile(outbuffer, solutions.get(0)._st);
        
        return st;
    }
    
    static private Options newOptions(int criterion, boolean rooted,
            boolean extrarooted, 
            double cs, double cd, double wh, 
            Set<String> keepOptions, JSAPResult config) {
    	Options options = new Options(
    			rooted, extrarooted, 
    			config.getBoolean("exact"), 
    			criterion > 0, 1, 
    			config.getInt("extraLevel"),
    			keepOptions.contains("completed"), 
    			keepOptions.contains("searchspace_norun") || keepOptions.contains("searchspace"), 
    			!keepOptions.contains("searchspace_norun"),
    			config.getInt("branch annotation level"), 
    			config.getDouble("lambda"));
    	options.setDLbdWeigth(wh); 
    	options.setCS(cs);
    	options.setCD(cd);
    	
    	return options;
    }

    private static AbstractInference initializeInference(int criterion, 
            List<Tree> trees, List<Tree> extraTrees,
            Options options) {
        AbstractInference inference;		
		if (criterion == 1 || criterion == 0) {
			inference = new DLInference(options, 
					trees, extraTrees);			
		} else if (criterion == 2) {
			inference = new WQInference(options, trees, 
					extraTrees );
		} else {
			throw new RuntimeException("criterion not set?");
		}		
        return inference;
    }

    private static List<String> readTreeFileAsString(File file)
    				throws FileNotFoundException, IOException {
    	String line;		
    	List<String> trees = new ArrayList<String>();
    	BufferedReader treeBufferReader = new BufferedReader(new FileReader(file));
		while ((line = treeBufferReader .readLine()) != null) {
    		if (line.length() > 0) {
    			line = line.replaceAll("\\)[^,);]*", ")");
    			trees.add(line);
    		}
    	}
    	treeBufferReader.close();
    	return trees;

    }

    private static List<Tree> readInputTrees(List<String> lines, 
    		boolean rooted, boolean checkCompleteness, boolean stLablel,
    		Integer minleaves, int annotation)
    				throws FileNotFoundException, IOException {
    	List<Tree> trees = new ArrayList<Tree>();
    	List<Integer> skipped = new Stack<Integer>();
    	int l = 0;			
    	try {
    		TreeSet<String> allleaves = new TreeSet<String>();
    		for (String line : lines) {
    			l++;
    			Set<String> previousTreeTaxa = new HashSet<String>();
    			if (line.length()  == 0) {
    				continue;
    			}
    			NewickReader nr = new NewickReader(new StringReader(line));
    			if (rooted) {
    				STITree<Double> gt = new STITree<Double>(true);
    				nr.readTree(gt);
    				if (checkCompleteness) {
    					if (previousTreeTaxa.isEmpty()) {
    						previousTreeTaxa.addAll(Arrays.asList(gt
    								.getLeaves()));
    					} else {
    						if (!previousTreeTaxa.containsAll(Arrays.asList(gt
    								.getLeaves()))) {
    							throw new RuntimeException(
    									"Not all trees are on the same set of taxa: "
    											+ gt.getLeaves() + "\n"
    											+ previousTreeTaxa);
    						}
    					}
    				}
    				if (minleaves == null || gt.getLeafCount() >= minleaves) {
    					trees.add(gt);
    				} else {
    					skipped.add(l);
    				}
    			} else {						
    				Tree tr = nr.readTree();
    				if (minleaves == null || tr.getLeafCount() >= minleaves) {
    					trees.add(tr);
    				} else {
    					skipped.add(l);
    				}
    				if (stLablel) {
    					GlobalMaps.taxonNameMap.getSpeciesIdMapper().stToGt((MutableTree) tr);
    				}
    				String[] leaves = tr.getLeaves().clone();
    				if (annotation != 6) {
	    				for (int i = 0; i < leaves.length; i++) {
	    					//if (!stLablel) {
	    						GlobalMaps.taxonIdentifier.taxonId(leaves[i]);
	    						//} else {
	    						//   GlobalMaps.taxonNameMap.getSpeciesIdMapper().speciesId(leaves[i]);
	    						//}
	    				}
    				} else{
    					allleaves.addAll(Arrays.asList(leaves));
    				}
    			}
    		}
    		if (annotation == 6) {
    			for (String leaf: allleaves) {
					GlobalMaps.taxonIdentifier.taxonId(leaf);
    			}
			}
    	} catch (ParseException e) {
    		throw new RuntimeException("Failed to Parse Tree number: " + l ,e);
    	}
    	if (skipped.size() > 0) {
    		System.err.println("Skipping the following tree(s) because they had less than " + minleaves+" leaves: \n" + skipped);
    	}
    	return trees;
    }


    private static void writeTreeToFile(BufferedWriter outbuffer, Tree t) {
        try {
		    outbuffer.write(t.toStringWD()+ " \n");
		    outbuffer.flush();
		} catch (IOException e) {
		    System.err.println("Error when writing the species tree");
		    System.err.println(e.getMessage());
		    e.printStackTrace();
		}
    }


}
