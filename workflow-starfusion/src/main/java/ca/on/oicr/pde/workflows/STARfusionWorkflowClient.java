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
  //*  private String moduleFile;
    
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
        file1.setSourcePath(read1Fastq);
        file1.setType(FASTQ_GZIP_MIMETYPE);
        file1.setIsInput(true);
        return this.getFiles();
    }

    @Override
    public void buildWorkflow() {

        /**
         * Steps for sequenza: 1. Check if "bam" file exists; true 2. Check if
         * "bai" file exists; true: go to step 4 3. Check if normal Pb_R sample
         * exists; true: go to step 4; else abort 3. If false: samtools index
         * "bam" file 4. Run job sequenza-utils 5. If outputFile ends with
         * "bin50.gz"; go to step 6; else go to step 4 6. Run job sequenzaR 7.
         * Iterate through the files/folders in outDir: 8. If fileName1 ==
         * "pandc.txt" and fileName2 ends with "Total_CN.seg"; create a folder
         * called "copynumber" 9. If fileType == "folder"; create a folder
         * called "model-fit"; move folders to "model-fit" 10. If fileType ==
         * "file" && fileName != outputFile; move file to "model-fit" 11. Delete
         * outputFile (rm outputFile) 12. zip "model-fit" 13. outputFile =
         * fileName2 14. OutputDir contains the following: fileName1,
         * outputFile, model-fit.zip
         */
        // workflow : read inputs read1 fastq and read2 fastq file; run star-fusion; write the output to temp directory; 
        // run sequenzaR; handle output; provision files (3) -- model-fit.zip; text/plain; text/plain
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
        cmd.addArgument("export LD_LIBRARY_PATH=/oicr/local/analysis/sw/perl/perl-5.22.2-tgl/lib:$LD_LIBRARY_PATH");
        cmd.addArgument("export PERL5LIB=/oicr/local/analysis/sw/perl/perl-5.22.2-tgl/lib:$PERL5LIB");
        cmd.addArgument("export PATH=/oicr/local/analysis/sw/perl/perl-5.22.2-tgl/bin:$PATH");
        cmd.addArgument("export PATH=/oicr/local/analysis/sw/star/STAR-2.6.0c/bin/Linux_x86_64_static:$PATH");
        cmd.addArgument("export PATH=/oicr/local/analysis/sw//samtools/samtools-0.1.17:$PATH");
        cmd.addArgument("export PATH=/oicr/local/analysis/sw/starfusion/STAR-Fusion-v1.4.0:$PATH");
        cmd.addArgument("STAR-Fusion");
        cmd.addArgument("--genome_lib_dir " + this.refGenome);
        cmd.addArgument("--left_fq " + this.read1Fastq);
        cmd.addArgument("--right_fq " + this.read2Fastq);
        cmd.addArgument("--examine_coding_effect");
        cmd.addArgument("--FusionInspector validate");
        cmd.addArgument("--output_dir " + this.tmpDir);
        starJob.setMaxMemory(Integer.toString(starfusionMem * 1024));
        starJob.setQueue(getOptionalProperty("queue", ""));
        return starJob;
    }}


