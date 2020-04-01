String org = 'sboardwell'
String repo = 'job-dsl-generator'
String branch = 'master'
String jobName = 'job-dsl-generator-example-projectA'
String scriptPath = 'examples/projectA/Jenkinsfile'
String credentialsId = 'jx-pipeline-git-github-github'

Class JobDslGenUtil = new GroovyClassLoader(Thread.currentThread().getContextClassLoader()).parseClass(new URL("${UTIL_SCRIPT_URL}${binding.hasVariable('UTIL_SCRIPT_TOKEN') ? "?token=${binding.getVariable('UTIL_SCRIPT_TOKEN')}" : ''}").text);

String jenkinsFile = JobDslGenUtil.getJenkinsfileFromGitHub(org, repo, branch, scriptPath, "${GITHUB_TOKEN ?: ''}")
println "${jenkinsFile}"

def pipelineJson = JobDslGenUtil.getJenkinsfileJsonObject(JENKINS_URL, JENKINS_CREDS, jenkinsFile, out)
println "${pipelineJson}"

// create in separate steps
def job1 = JobDslGenUtil.generateJobBase(this, jobName + 'ex1', org, repo, branch, credentialsId, scriptPath, '', '')
JobDslGenUtil.addParameters(job1, pipelineJson, out)
JobDslGenUtil.addOptions(job1, pipelineJson, out)
JobDslGenUtil.addTriggers(job1, pipelineJson, out)

// create in one step
def job2 = JobDslGenUtil.generateJobFull(this, jobName + 'ex2', org, repo, branch, credentialsId, scriptPath, '', '', pipelineJson, out)
