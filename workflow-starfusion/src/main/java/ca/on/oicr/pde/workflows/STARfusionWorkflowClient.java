package ca.on.oicr.pde.workflows;

import ca.on.oicr.pde.utilities.workflows.OicrWorkflow;
import java.util.Map;
import java.util.logging.Logger;
import net.sourceforge.seqware.pipeline.workflowV2.model.Command;
import net.sourceforge.seqware.pipeline.workflowV2.model.Job;
import net.sourceforge.seqware.pipeline.workflowV2.model.SqwFile;

/**
 * <p>
 * For more information on developing workflows, see the documentation at
 * <a href="http://seqware.github.io/docs/6-pipeline/java-workflows/">SeqWare
 * Java Workflows</a>.</p>
 *
 * Quick reference for the order of methods called: 1. setupDirectory 2.
 * setupFiles 3. setupWorkflow 4. setupEnvironment 5. buildWorkflow
 *
 * See the SeqWare API for
 * <a href="http://seqware.github.io/javadoc/stable/apidocs/net/sourceforge/seqware/pipeline/workflowV2/AbstractWorkflowDataModel.html#setupDirectory%28%29">AbstractWorkflowDataModel</a>
 * for more information.
 */
public class STARfusionWorkflowClient extends OicrWorkflow {

    //dir
    private String dataDir, tmpDir;
    private String outDir;

    // Input Data
    private String read1Fastq;
    private String read2Fastq;
    private String outputFilenamePrefix;
  
    // programs
    private String ld_library_path;
    private String perl5lib; 
    private String path; 
    private String path2; 
    private String path3; 
    private String path4; 
    private String ld_library_path_2; 
    private String path5;
    private String perl5lib_2;
    private String tabixroot; 
    
    //Memory allocation
    private Integer starfusionMem;
 

    //path to bin
    private String bin;

    //ref Data
    private String refGenome;


    private boolean manualOutput;
    private static final Logger logger = Logger.getLogger(STARfusionWorkflowClient.class.getName());
    private String queue;
    private Map<String, SqwFile> tempFiles;

    // meta-types
    private final static String TXT_METATYPE = "text/plain";
    private final static String TAR_GZ_METATYPE = "application/tar-gzip";
    private static final String FASTQ_GZIP_MIMETYPE = "chemical/seq-na-fastq-gzip";

    private void init() {
        try {
            //dir
            dataDir = "data";
            tmpDir = getProperty("tmp_dir");
            
            //program
           // moduleFile = getProperty("moduleLoadFile");

            // input samples 
            read1Fastq = getProperty("input_read1_fastq");
            read2Fastq = getProperty("input_read2_fastq");

            //Ext id
            outputFilenamePrefix = getProperty("external_name");

            // Tools
            ld_library_path = getProperty("LD_LIBRARY_PATH");
            perl5lib = getProperty("PERL5LIB");
            path = getProperty("PATH");
            path2 = getProperty("PATH2");
            path3 = getProperty("PATH3");
            path4 = getProperty("PATH4");
            ld_library_path_2 = getProperty("LD_LIBRARY_PATH_2");
            path5 = getProperty("PATH5");
            perl5lib_2 = getProperty("PERL5LIB_2");
            tabixroot = getProperty("TABIXROOT");
            
            // ref fasta
            refGenome = getProperty("ref_genome");
           

         
            manualOutput = Boolean.parseBoolean(getProperty("manual_output"));
            queue = getOptionalProperty("queue", "");

            // starfusion
                starfusionMem = Integer.parseInt(getProperty("starfusion_mem"));
            
            
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setupDirectory() {
        init();
        this.addDirectory(dataDir);
        this.addDirectory(tmpDir);
        if (!dataDir.endsWith("/")) {
            dataDir += "/";
        }
        if (!tmpDir.endsWith("/")) {
            tmpDir += "/";
        }
    }

    @Override
    public Map<String, SqwFile> setupFiles() {
        SqwFile file0 = this.createFile("read1");
        file0.setSourcePath(read1Fastq);
        file0.setType(FASTQ_GZIP_MIMETYPE);
        file0.setIsInput(true);
        SqwFile file1 = this.createFile("read2");
        file1.setSourcePath(read2Fastq);
        file1.setType(FASTQ_GZIP_MIMETYPE);
        file1.setIsInput(true);
        return this.getFiles();
    }

    @Override
    public void buildWorkflow() {

        /**
         * STAR-Fusion: 
         */
        // workflow : read inputs read1 fastq and read2 fastq file; run star-fusion; write the output to temp directory; 
        // run sequenzaR; handle output; provision files (3) -- .tsv, .tsv, .tsv;
        Job parentJob = null;
        this.outDir = this.outputFilenamePrefix + "_output";

   
        Job starJob = runStarFusion();
        parentJob = starJob;
      

        // Provision .seg, .varscanSomatic_confints_CP.txt, model-fit.tar.gz files
        String fusionPredictionTsv = this.tmpDir + "star-fusion.fusion_predictions.tsv";
        SqwFile fusionTSV = createOutputFile(fusionPredictionTsv, TXT_METATYPE, this.manualOutput);
        fusionTSV.getAnnotations().put("STAR_fusion_prediction_tsv", "STAR_fusion");
        starJob.addFile(fusionTSV);

        String fusionAbridgedTsv = this.tmpDir + "star-fusion.fusion_predictions.abridged.tsv";
        SqwFile abridgedTSV = createOutputFile(fusionAbridgedTsv, TXT_METATYPE, this.manualOutput);
        abridgedTSV.getAnnotations().put("STAR_fusion_abridged_tsv", "STAR_fusion");
        starJob.addFile(abridgedTSV);
        
        String FFP_coding_effect = this.tmpDir + "star-fusion.fusion_predictions.abridged.coding_effect.tsv";
        SqwFile codingTSV = createOutputFile(FFP_coding_effect, TXT_METATYPE, this.manualOutput);
        codingTSV.getAnnotations().put("STAR_fusion_coding_effect_tsv ", "STAR_fusion");
        starJob.addFile(codingTSV);
          }

  
    private Job runStarFusion() {
        Job starJob = getWorkflow().createBashJob("starfusionjob");
        Command cmd = starJob.getCommand();
        cmd.addArgument("export " + this.ld_library_path +";");
        cmd.addArgument("export " + this.perl5lib + ";");
        cmd.addArgument("export " + this.path + ";");
        cmd.addArgument("export " + this.path2 + ";");
        cmd.addArgument("export " + this.path3 + ";");
        cmd.addArgument("export " + this.path4 + ";");
        cmd.addArgument("export " + this.ld_library_path_2 + ";");
        cmd.addArgument("export " + this.path5 + ";");
        cmd.addArgument("export " + this.perl5lib_2 +";");
        cmd.addArgument("export " + this.tabixroot + ";");
        cmd.addArgument("STAR-Fusion");
        cmd.addArgument("--genome_lib_dir " + this.refGenome);
        cmd.addArgument("--left_fq " + getFiles().get("read1").getProvisionedPath());
        cmd.addArgument("--right_fq " + getFiles().get("read2").getProvisionedPath());
        cmd.addArgument("--examine_coding_effect");
        cmd.addArgument("--FusionInspector validate");
        cmd.addArgument("--output_dir " + this.tmpDir);
        starJob.setMaxMemory(Integer.toString(starfusionMem * 1024));
        starJob.setQueue(getOptionalProperty("queue", ""));
        return starJob;
    }}

