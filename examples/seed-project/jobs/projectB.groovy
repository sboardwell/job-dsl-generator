String org = 'sboardwell'
String repo = 'job-dsl-generator'
String branch = 'master'
String jobName = 'job-dsl-generator-example-projectB'
String scriptPath = 'examples/projectB/Jenkinsfile'
String credentialsId = 'jx-pipeline-git-github-github'

Class JobDslGenUtil = new GroovyClassLoader(Thread.currentThread().getContextClassLoader()).parseClass(new URL("${UTIL_SCRIPT_URL}${binding.hasVariable('UTIL_SCRIPT_TOKEN') ? "?token=${binding.getVariable('UTIL_SCRIPT_TOKEN')}" : ''}").text);

// generate the job completely from scratch
JobDslGenUtil.generateJobFromScratch(
    this, 
    jobName, 
    org, 
    repo, 
    branch, 
    credentialsId, 
    scriptPath, 
    "${jobName} Fancy Display", 
    "Description of project B (${jobName})!", 
    "${GITHUB_TOKEN ?: ''}", 
    JENKINS_URL, 
    JENKINS_CREDS
)
