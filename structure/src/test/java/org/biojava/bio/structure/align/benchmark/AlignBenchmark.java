/**
 * 
 */
package org.biojava.bio.structure.align.benchmark;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.biojava.bio.structure.Atom;
import org.biojava.bio.structure.StructureException;
import org.biojava.bio.structure.align.StructureAlignment;
import org.biojava.bio.structure.align.StructureAlignmentFactory;
import org.biojava.bio.structure.align.benchmark.metrics.Metric;
import org.biojava.bio.structure.align.ce.CeMain;
import org.biojava.bio.structure.align.ce.CeParameters;
import org.biojava.bio.structure.align.model.AFPChain;
import org.biojava.bio.structure.align.util.AtomCache;

/**
 * @author Spencer Bliven
 *
 */
public class AlignBenchmark {
	private AtomCache cache;
	private StructureAlignment aligner;
	private int maxLength; // Don't try alignments between proteins which are too long.
	private List<Metric> metrics;

	public AlignBenchmark(String cachePath, StructureAlignment method, int maxLength) {
		this.cache = new AtomCache(cachePath, true);
		this.aligner = method;
		this.maxLength = maxLength;
		this.metrics = AlignmentStats.defaultMetrics();
	}

	/* TODO write a test which makes sure that CeMain gives good performance against RIPC
	@Test
	public void runRIPCBenchmark() {
		String RIPCfile = "src/test/resources/align/benchmarks/RIPC.align";
		MultipleAlignmentParser parser = new RIPCParser(RIPCfile);
		runBenchmark(parser);
	}
	 */

	public void runBenchmark(Writer out, MultipleAlignmentParser parser) {

		try {
			AlignmentStatsIterator statsIt = new AlignmentStatsIterator(parser,
					aligner, metrics, this.maxLength );


			AlignmentStats.writeHeader(out, metrics);
			while(statsIt.hasNext()) {
				AlignmentStats stats = statsIt.next();
				stats.writeStats(out);
				out.flush();
			}

		} catch (Exception e) {
			e.printStackTrace();
			return;
		}
		/*try {
			List<Metric> metrics = AlignmentStats.defaultMetrics();
			Writer stdout = new BufferedWriter(new OutputStreamWriter(System.err));
			stdout.write("PDB1\tPDB2\t");
			AlignmentStats.writeHeader(stdout,metrics);

			stdout.flush();

			for(MultipleAlignment ma : parser ) {
				//Parse labels & get the structures
				String[] pdbIDs = ma.getNames();
				List<Atom[]> structures = new ArrayList<Atom[]>(pdbIDs.length);

				// Get alignment structures
				for(int i=0;i<pdbIDs.length;i++) {
					String pdb1 = pdbIDs[i];

					Atom[] ca1 = this.cache.getAtoms(pdb1);

					if(ca1.length > maxLength) {
						System.out.format("Skipping large protein '%s' of length %d\n", pdb1, ca1.length);
						structures.add(null);
					}
					structures.add(ca1);
				}


				// For each pair of structures, find the alignment
				for(int i=0;i<pdbIDs.length-1;i++) {
					Atom[] ca1 = structures.get(i);

					if(ca1 == null)
						continue;

					for(int j=i+1;j<pdbIDs.length;j++) {
						Atom[] ca2 = structures.get(j);

						if(ca2 == null)
							continue;

						//System.out.format("Comparing %s to %s\n",pdbIDs[i],pdbIDs[j]);

						AFPChain alignment = ceMain.align(ca1,ca2);
						//System.out.format("Done Comparing %s to %s\n",pdbIDs[i],pdbIDs[j]);

						stdout.write(String.format("%s\t%s\t",pdbIDs[i],pdbIDs[j]));
						metrics.writeStats(stdout, ma, alignment, ca1, ca2);
						stdout.flush();

					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		 */
	}

	/**
	 * 
	 * @author Spencer Bliven
	 *
	 */
	// TODO test that this works with MultipleAlignments of more than two proteins
	protected class AlignmentStatsIterator implements Iterator<AlignmentStats> {
		private List<Metric> metrics;

		private Iterator<MultipleAlignment> parser;
		private final int maxLength;
		private StructureAlignment aligner;

		private MultipleAlignment currentAlignment;
		private List<Atom[]> structures; //Atoms for each protein in the currentAlignment
		private int nextAlignLeft; //index to one protein in the currentAlignment
		private int nextAlignRight; //index to another protein in the currentAlignment

		public AlignmentStatsIterator( MultipleAlignmentParser parser,
				StructureAlignment aligner, List<Metric> metrics)
		throws StructureException, IOException
		{
			this(parser,aligner,metrics,-1);
		}

		public AlignmentStatsIterator( MultipleAlignmentParser parser,
				StructureAlignment aligner, List<Metric> metrics, int maxLength)
		throws StructureException, IOException
		{
			this.parser = parser.iterator();
			this.maxLength = maxLength;
			this.aligner = aligner;

			this.metrics = metrics;

			if(this.parser.hasNext()) {
				this.currentAlignment = this.parser.next();

				// Get alignment structures
				String[] pdbIDs = this.currentAlignment.getNames();
				this.structures= new ArrayList<Atom[]>(pdbIDs.length);

				for(int i=0;i<pdbIDs.length;i++) {
					String pdb1 = pdbIDs[i];

					Atom[] ca1 = cache.getAtoms(pdb1);

					if(ca1.length > this.maxLength && this.maxLength > 0) {
						System.err.format("Skipping large protein '%s' of length %d\n", pdb1, ca1.length);
						this.structures.add(null);
					} else {
						this.structures.add(ca1);
					}
				}

			} else {
				this.currentAlignment = null;
				this.structures = null;
			}
			this.nextAlignLeft=0;
			this.nextAlignRight=1;

		}

		/**
		 * @see java.util.Iterator#next()
		 */
		//@Override
		public AlignmentStats next() {
			while(true) { // Eventually returns or throws exception
				//String[] pdbIDs = this.currentAlignment.getNames();
				int alignLen = this.currentAlignment.getNames().length;

				while( nextAlignLeft<alignLen-1 ) {
					Atom[] ca1 = structures.get(nextAlignLeft);

					if(ca1 == null) {
						nextAlignLeft++;
						nextAlignRight = nextAlignLeft+1;
						continue;
					}

					while( nextAlignRight<alignLen) {
						Atom[] ca2 = structures.get(nextAlignRight);

						//Increment loop counter
						nextAlignRight++;

						if(ca2 == null) {
							continue;
						}

						//System.out.format("Comparing %s to %s\n",pdbIDs[i],pdbIDs[j]);
						AFPChain alignment;
						try {
							alignment = aligner.align(ca1,ca2);
						} catch (StructureException e) {
							e.printStackTrace();
							return null;
						}
						//System.out.format("Done Comparing %s to %s\n",pdbIDs[i],pdbIDs[j]);

						return new AlignmentStats(this.metrics,this.currentAlignment, alignment, ca1, ca2);
					}

					nextAlignLeft++;
					nextAlignRight = nextAlignLeft+1;

				}


				// Nothing left for currentAlignment. Go on to next one.
				if(this.parser.hasNext()) {
					// Reset the currentAlignment
					this.currentAlignment = this.parser.next();
					nextAlignLeft = 0;
					nextAlignRight = 1;

					// Get alignment structures
					String[] pdbIDs = this.currentAlignment.getNames();

					this.structures= new ArrayList<Atom[]>(pdbIDs.length);

					for(int i=0;i<pdbIDs.length;i++) {
						String pdb1 = pdbIDs[i];

						Atom[] ca1;
						try {
							ca1 = cache.getAtoms(pdb1);
						} catch (StructureException e) {
							e.printStackTrace();
							return null;
						} catch (IOException e) {
							e.printStackTrace();
							return null;
						}

						if(ca1.length > this.maxLength && this.maxLength > 0) {
							System.err.format("Skipping large protein '%s' of length %d\n", pdb1, ca1.length);
							this.structures.add(null);
						} else {
							this.structures.add(ca1);
						}
					}
				}
				else {
					throw new IllegalStateException("No remaining items in iterator");
				}

			} // Return to loop start

		}

		/**
		 * @see java.util.Iterator#hasNext()
		 */
		//@Override
		public boolean hasNext() {
			int alignLen = this.currentAlignment.getNames().length;

			return this.parser.hasNext() ||
			nextAlignLeft >= alignLen-1 ||
			nextAlignRight<alignLen;
		}

		/**
		 * @see java.util.Iterator#remove()
		 */
		//@Override
		public void remove() {
			throw new UnsupportedOperationException("remove() not implemented");
		}

	}

	public static void main(String[] args) {
		//// Set parameters
		// TODO make these arguments
		int maxLength = 1000; // Don't try alignments between proteins which are too long.
		String inFile = null;
		String outFile = null;
		MultipleAlignmentParser parser;
		StructureAlignment aligner;

		// Argument parsing
		String usage = "usage: AlignBenchmark parser alignment [infile [outfile [maxLength]]]\n" +
		"  parser:    Either RIPC or CPDB\n" +
		"  alignment: Either CE or CE-CP\n" +
		"  infile:    If empty or omitted, gets alignments from biojava resource directory\n" +
		"  outfile:   If empty, omitted or \"-\", uses standard out\n" +
		"  maxLength: Longest protein to compare. 0 disables. Default 1000";

		if(args.length < 2) {
			System.err.println("Error: Too few args!");
			System.err.println(usage);
			System.exit(1);
			return;
		}

		// DO arg 0 (parser type) at the end
		int arg = 1;

		String alignerName = args[arg];
		if(alignerName.matches("[cC][eE]|[cC][eE].?[cC][pP]")) {
			CeMain ceMain;
			try {
				ceMain = (CeMain) StructureAlignmentFactory.getAlgorithm(CeMain.algorithmName);
				if(alignerName.matches("[cC][eE].?[cC][pP]")) {
					((CeParameters)ceMain.getParameters()).setCheckCircular(true);
				}
				((CeParameters)ceMain.getParameters()).setMaxGapSize(0);
			} catch (StructureException e) {
				e.printStackTrace();
				System.exit(1);
				return;
			}
			aligner = ceMain;
		}
		else {
			System.err.println("Unrecognized aligner ("+alignerName+"). Choose from: CE, CE-CP");
			System.err.println(usage);
			System.exit(1);
			return;
		}
		arg++;

		if(args.length > arg && !args[arg].equals("")) {
			inFile = args[arg];
		}
		arg++;

		if(args.length > arg && !args[arg].equals("")) {
			outFile = args[arg];
		}
		arg++;

		if(args.length > arg) {
			try {
				maxLength = Integer.parseInt(args[arg]);
			} catch(NumberFormatException e) {
				System.err.println("Unrecognized maximum length ("+args[arg]+").");
				System.err.println(usage);
				System.exit(1);
				return;
			}
		}
		arg++;

		if(args.length > arg) {
			System.err.println("Error: Too many args!");
			System.err.println(usage);
			System.exit(1);
			return;
		}
		arg++;

		String fileType = args[0];
		if(fileType.equalsIgnoreCase("RIPC")) {
			if(inFile == null )
				inFile = "src/test/resources/align/benchmarks/RIPC.align";
			//outFile = "/Users/blivens/dev/bourne/benchmarks/RIPC.stats";
			parser = new RIPCParser(inFile);
		}
		else if(fileType.equalsIgnoreCase("CPDB")) {
			if(inFile == null )
				inFile = "src/test/resources/align/benchmarks/CPDB_CPpairsAlignments_withCPSiteRefinement.txt";
			//outFile = "/Users/blivens/dev/bourne/benchmarks/CPDB.stats";
			parser = new CPDBParser(inFile);
		}
		else {
			System.err.println("Unrecognized parser. Choose from: RIPC, CPDB");
			System.err.println(usage);
			System.exit(1);
			return;
		}

		//// Done with parameters

		Writer out;
		out = new BufferedWriter(new OutputStreamWriter(System.out));
		if( outFile != null && !(outFile.equals("") || outFile.equals("-") )) {
			try {
				out = new BufferedWriter(new FileWriter(outFile));
			} catch (IOException e1) {
				e1.printStackTrace();
				System.err.println("Error writing file "+outFile);
				System.exit(1);
				return;
			}
		}

		AlignBenchmark bm = new AlignBenchmark("/tmp/",aligner,maxLength);
		bm.runBenchmark(out, parser);
	}

}