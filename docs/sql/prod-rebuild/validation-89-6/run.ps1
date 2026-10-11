param([switch]$Resume,[ValidateSet('Probe')][string]$Mode='Probe')
$ErrorActionPreference='Stop'
$work=$PSScriptRoot
$repo=(Resolve-Path (Join-Path $work '../../../..')).Path
$pgsql='D:\dev\GWON_System\ai-lab\isolated-tools\postgresql-17.11\pgsql'
$jdk='C:\Program Files\Java\jdk-21.0.10\bin'
$data=Join-Path $work 'pgdata-89-6-final'
if($Resume){
 if(!(Test-Path -LiteralPath "$data/PG_VERSION")){throw 'This step new cluster missing'}
 & "$pgsql\bin\pg_ctl.exe" -D $data status
 if($LASTEXITCODE -ne 3){throw 'This step cluster not stopped'}
}elseif(Test-Path -LiteralPath $data){throw 'Existing pgdata: refuse initialization'}
if(netstat -ano -p tcp | Select-String '^\s*TCP\s+\S+:55496\s'){throw 'Port 55496 occupied'}
$version=& "$pgsql\bin\postgres.exe" --version
if($LASTEXITCODE -or $version -notmatch '17\.11'){throw 'PostgreSQL version mismatch'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$libs=Join-Path $work 'lib'
if(!(Test-Path -LiteralPath $libs)){
New-Item -ItemType Directory -Path $libs | Out-Null
$archive=[IO.Compression.ZipFile]::OpenRead("$repo\target\backend-0.0.1-SNAPSHOT.jar")
try{foreach($entry in $archive.Entries){if($entry.FullName -like 'BOOT-INF/lib/*.jar'){
 [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libs $entry.Name),$false)
}}}finally{$archive.Dispose()}
}
$cp="$work;$repo\target\classes;$libs\*"
& "$jdk\javac.exe" -cp $cp -d $work "$work\Probe.java" *> "$work\compile.log"
if($LASTEXITCODE){throw 'Probe compilation failed'}
Get-FileHash "$work\..\*.sql","$work\Probe.java","$repo\target\backend-0.0.1-SNAPSHOT.jar" -Algorithm SHA256 |
 Select-Object Path,Hash | ConvertTo-Json | Set-Content "$work\input-hashes.json"
$saved=@{}
foreach($name in @('PGHOST','PGHOSTADDR','PGPORT','PGDATABASE','PGUSER','PGPASSWORD','PGSERVICE','PGSERVICEFILE','PGPASSFILE','PGOPTIONS','PGSSLMODE','PGDATA','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){
 $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
 Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
}
$attempt=$false
try{
 if(!$Resume){
  & "$pgsql\bin\initdb.exe" -D $data -L "$pgsql\share" -U isolated_admin --encoding=UTF8 --locale=C --auth-local=trust --auth-host=trust *> "$work\initdb.log"
  if($LASTEXITCODE){throw 'New isolated initdb failed'}
 }
 $attempt=$true
 $arguments='-D "'+$data+'" -p "'+$pgsql+'\bin\postgres.exe" -l "'+$work+'\server.log" -o "-h 127.0.0.1 -p 55496 -c ssl=off" -w -t 30 start'
 $taskProcess=Start-Process -FilePath "$pgsql\bin\pg_ctl.exe" -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput "$work\start.log" -RedirectStandardError "$work\start.err"
 $taskProcess.WaitForExit()
 if($taskProcess.ExitCode){throw 'Isolated startup failed'}
 & "$pgsql\bin\pg_isready.exe" -h 127.0.0.1 -p 55496 -U isolated_admin -d postgres *> "$work\ready.log"
 if($LASTEXITCODE){throw 'Readiness failed'}
 & "$jdk\java.exe" -cp $cp $Mode $work *> "$work\$Mode.log"
 if($LASTEXITCODE){throw 'Probe failed; see local probe.log'}
}finally{
 if($attempt){
  # Exact NEW directory only, verified by Probe before any business SQL.
  $taskProcess=Start-Process -FilePath "$pgsql\bin\pg_ctl.exe" -ArgumentList ('-D "'+$data+'" -m fast -w -t 30 stop') -WindowStyle Hidden -PassThru -RedirectStandardOutput "$work\stop.log" -RedirectStandardError "$work\stop.err"
  $taskProcess.WaitForExit();$stopCode=$taskProcess.ExitCode
  & "$pgsql\bin\pg_ctl.exe" -D $data status *> "$work\status-after.log";$statusCode=$LASTEXITCODE
  & "$pgsql\bin\pg_isready.exe" -h 127.0.0.1 -p 55496 -t 3 *> "$work\ready-after.log";$readyCode=$LASTEXITCODE
  "stop=$stopCode status=$statusCode ready=$readyCode" | Set-Content "$work\shutdown-result.txt"
  if($stopCode -ne 0 -or $statusCode -ne 3 -or $readyCode -ne 2){Write-Error 'Shutdown could not be verified'}
 }
 foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
