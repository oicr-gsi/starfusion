# starFusion

STAR-Fusion 1.8.1

## Overview

## Dependencies

* [star 2.7.3a](https://github.com/alexdobin/STAR)
* [star-fusion 1.8.1](https://github.com/STAR-Fusion/STAR-Fusion/wiki)


## Usage

### Cromwell
```
java -jar cromwell.jar run starFusion.wdl --inputs inputs.json
```

### Inputs

#### Required workflow parameters:
Parameter|Value|Description
---|---|---
`fastq1`|File|Path to the fastq file for read 1
`fastq2`|File|Path to the fastq file for read 2


#### Optional workflow parameters:
Parameter|Value|Default|Description
---|---|---|---
`chimeric`|File?|None|Path to Chimeric.out.junction


#### Optional task parameters:
Parameter|Value|Default|Description
---|---|---|---
`runStarFusion.starFusion`|String?|"$STAR_FUSION_ROOT/STAR-Fusion"|Name of the STAR-Fusion binary
`runStarFusion.cpu`|Int?|8|Number of CPU nodes to use
`runStarFusion.modules`|String?|"star-fusion/1.8.1 star-fusion-genome/1.8.1-hg38"|Names and versions of STAR-Fusion and STAR-Fusion genome to load
`runStarFusion.genomeDir`|String?|"$STAR_FUSION_GENOME_ROOT/ctat_genome_lib_build_dir"|Path to the STAR-Fusion genome directory


### Outputs

Output | Type | Description
---|---|---
`fusions`|File|None
`fusionsAbridged`|File|None
`fusionCodingEffects`|File|None


## Niassa + Cromwell

This WDL workflow is wrapped in a Niassa workflow (https://github.com/oicr-gsi/pipedev/tree/master/pipedev-niassa-cromwell-workflow) so that it can used with the Niassa metadata tracking system (https://github.com/oicr-gsi/niassa).

* Building
```
mvn clean install
```

* Testing
```
mvn clean verify \
-Djava_opts="-Xmx1g -XX:+UseG1GC -XX:+UseStringDeduplication" \
-DrunTestThreads=2 \
-DskipITs=false \
-DskipRunITs=false \
-DworkingDirectory=/path/to/tmp/ \
-DschedulingHost=niassa_oozie_host \
-DwebserviceUrl=http://niassa-url:8080 \
-DwebserviceUser=niassa_user \
-DwebservicePassword=niassa_user_password \
-Dcromwell-host=http://cromwell-url:8000
```

## Support

For support, please file an issue on the [Github project](https://github.com/oicr-gsi) or send an email to gsi@oicr.on.ca .

_Generated with wdl_doc_gen (https://github.com/oicr-gsi/wdl_doc_gen/)_