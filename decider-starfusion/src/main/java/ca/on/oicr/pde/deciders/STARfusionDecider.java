package ca.on.oicr.pde.deciders;

import com.google.common.collect.Sets;
import java.util.*;
import net.sourceforge.seqware.common.hibernate.FindAllTheFiles;
import net.sourceforge.seqware.common.hibernate.FindAllTheFiles.Header;
import net.sourceforge.seqware.common.module.FileMetadata;
import net.sourceforge.seqware.common.module.ReturnValue;
import net.sourceforge.seqware.common.module.ReturnValue.ExitStatus;
import net.sourceforge.seqware.common.util.Log;

/**
 *
 * @author abenawra@oicr.on.ca
 */
public class STARfusionDecider extends OicrDecider {

    private String starfusionMemory = "64"; 
    private String queue = "";
    private String refGenomeDir = "/oicr/local/analysis/sw/starfusion/STAR-Fusion-v1.4.0/genomes/GRCh37_v19_CTAT_lib_Feb092018/ctat_genome_lib_build_dir";
    private String fusionInspect = "validate";
    private String samtools = "/oicr/local/analysis/sw//samtools/samtools-1.2/bin";


    private String templateType = "WT";

    private String read1;
    private String read2;
    private String external_name;
    private ReadGroupData readGroupDataForWorkflowRun;

    public STARfusionDecider() {
        super();
        parser.accepts("ini-file", "Optional: the location of the INI file.").withRequiredArg();
        parser.accepts("starfusion-mem", "Optional: StarFusion allocated memory Gb, default is 64.").withRequiredArg();
        parser.accepts("ref-genome-dir").withOptionalArg();
        parser.accepts("template-type", "Optional: limit the run to only specified template type(s). Default is WT").withOptionalArg();
        parser.accepts("samtools-dir", "Optional: Provide the path to samtools directory. Default is provided by the workflow ini").withOptionalArg();
        parser.accepts("fusion-inspect", "Optional: FusionInspector option (validate or inspect). Default is validate").withOptionalArg();
    }

    @Override
    public ReturnValue init() {
        Log.debug("INIT");
        this.setMetaType(Arrays.asList("chemical/seq-na-fastq-gzip"));
        this.setHeadersToGroupBy(Arrays.asList(FindAllTheFiles.Header.IUS_SWA));

        //allows anything defined on the command line to override the defaults here.
        if (this.options.has("starfusion-mem")) {
            this.starfusionMemory = options.valueOf("starfusion-mem").toString();
        }
        if (this.options.has("template-type")) {
            templateType = this.options.valueOf("template-type").toString();
            //templateType = Sets.newHashSet(templateTypeArg.split(","));
            if (!templateType.equals("WT")){
                Log.warn("Workflow may fail for templae type " + templateType + "; Template type must be WT");
            }
        }
        if(this.options.has("ref-genome-dir")){
            this.refGenomeDir = options.valueOf("ref-genome-dir").toString();
        }
        if(this.options.has("fusion-inspect")){
            this.fusionInspect = options.valueOf("fusion-inspect").toString();
        }
        if(this.options.has("samtools-dir")){
            this.samtools = options.valueOf("samtools-dir").toString();
        }
        ReturnValue val = super.init();
        return val;
    }

    @Override
    protected ReturnValue doFinalCheck(String commaSeparatedFilePaths, String commaSeparatedParentAccessions) {
        this.read1 = null;
        this.read2 = null;

        String[] filePaths = commaSeparatedFilePaths.split(",");
        if (filePaths.length != 2) {
            Log.error("This Decider supports only cases where we have only 2 files per lane, WON'T RUN");
            return new ReturnValue(ReturnValue.INVALIDPARAMETERS);
        }

        String[] fqFilesArray = commaSeparatedFilePaths.split(",");
        for (String file : fqFilesArray) {
            int mate = idMate(file);
            switch (mate) {
                case 1:
                    if (this.read1 != null) {
                        Log.error("More than one file found for read 1: " + read1 + ", " + file);
                        return new ReturnValue(ExitStatus.INVALIDFILE);
                    }
                    this.read1 = file;
                    break;
                case 2:
                    if (this.read2 != null) {
                        Log.error("More than one file found for read 2: " + read2 + ", " + file);
                        return new ReturnValue(ExitStatus.INVALIDFILE);
                    }
                    this.read2 = file;
                    break;
                default:
                    Log.error("Cannot identify " + file + " end (read 1 or 2)");
                    return new ReturnValue(ExitStatus.INVALIDFILE);
            }
        }

        if (read1 == null || read2 == null) {
            Log.error("The Decider was not able to find both R1 and R2 fastq files for paired sequencing alignment, WON'T RUN");
            return new ReturnValue(ReturnValue.INVALIDPARAMETERS);
        }

        readGroupDataForWorkflowRun = new ReadGroupData(files.get(read1), files.get(read2));

        return super.doFinalCheck(commaSeparatedFilePaths, commaSeparatedParentAccessions);
    }

    @Override
    protected boolean checkFileDetails(ReturnValue returnValue, FileMetadata fm) {
        Log.debug("CHECK FILE DETAILS:" + fm);

        if (templateType != null) {
            String currentTemplateType = returnValue.getAttribute(FindAllTheFiles.Header.SAMPLE_TAG_PREFIX.getTitle() + "geo_library_source_template_type");
            if (!templateType.equals(currentTemplateType)) {
                return false;
            }
        }
        this.external_name = returnValue.getAttribute(Header.SAMPLE_NAME.getTitle());

        return super.checkFileDetails(returnValue, fm);
    }

    @Override
    protected Map<String, String> modifyIniFile(String commaSeparatedFilePaths, String commaSeparatedParentAccessions) {
        Log.debug("INI FILE:" + commaSeparatedFilePaths);

        Map<String, String> iniFileMap = super.modifyIniFile(commaSeparatedFilePaths, commaSeparatedParentAccessions);
        iniFileMap.put("input_read1_fastq", this.read1);
        iniFileMap.put("input_read2_fastq", this.read2);

        iniFileMap.put("starfusion_mem", this.starfusionMemory);
        iniFileMap.put("external_name", this.external_name);
        
        iniFileMap.put("ref_genome_dir", this.refGenomeDir);
        iniFileMap.put("samtools", this.samtools);
        iniFileMap.put("fusion_inspect", this.fusionInspect);

        return iniFileMap;
    }

    public static void main(String args[]) {

        List<String> params = new ArrayList<String>();
        params.add("--plugin");
        params.add(STARfusionDecider.class.getCanonicalName());
        params.add("--");
        params.addAll(Arrays.asList(args));
        System.out.println("Parameters: " + Arrays.deepToString(params.toArray()));
        net.sourceforge.seqware.pipeline.runner.PluginRunner.main(params.toArray(new String[params.size()]));

    }

}
