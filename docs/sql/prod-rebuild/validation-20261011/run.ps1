$ErrorActionPreference='Stop'
$work=$PSScriptRoot
$pgsql='D:\dev\GWON_System\ai-lab\isolated-tools\postgresql-17.11\pgsql'
$repo='D:\dev\GWON_System\backend\notice-member-author-backend'
$data=Join-Path $work 'pgdata'
$jdk='C:\Program Files\Java\jdk-21.0.10\bin'
if(Test-Path -LiteralPath $data){throw 'Existing pgdata: refuse initialization'}
if(netstat -ano -p tcp | Select-String '^\s*TCP\s+\S+:55483\s'){throw 'Port55483 already in use'}
& "$pgsql\bin\postgres.exe" --version
if($LASTEXITCODE){throw 'Version check failed'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$libs=Join-Path $work 'lib'
New-Item -ItemType Directory -Path $libs | Out-Null
$archive=[IO.Compression.ZipFile]::OpenRead("$repo\target\backend-0.0.1-SNAPSHOT.jar")
try {foreach($entry in $archive.Entries){if($entry.FullName -like 'BOOT-INF/lib/*.jar'){
 [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,(Join-Path $libs $entry.Name),$false)
}}}finally{$archive.Dispose()}
$cp="$work;$repo\target\classes;$libs\*"
& "$jdk\javac.exe" -cp $cp -d $work "$work\Probe.java" *> "$work\compile.log"
if($LASTEXITCODE){throw 'Probe compilation failed'}
$saved=@{}
foreach($name in @('PGHOST','PGHOSTADDR','PGPORT','PGDATABASE','PGUSER','PGPASSWORD','PGSERVICE','PGSERVICEFILE','PGPASSFILE','PGOPTIONS','PGSSLMODE','PGDATA','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS')){
 $saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')
 Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue
}
$started=$false
try {
 & "$pgsql\bin\initdb.exe" -D $data -L "$pgsql\share" -U isolated_admin --encoding=UTF8 --locale=C --auth-local=trust --auth-host=trust *> "$work\initdb.log"
 if($LASTEXITCODE){throw 'Isolated initdb failed'}
 & "$pgsql\bin\pg_ctl.exe" -D $data -p "$pgsql\bin\postgres.exe" -l "$work\server.log" -o '-h 127.0.0.1 -p 55483 -c ssl=off' -w -t 60 start *> "$work\start.log"
 if($LASTEXITCODE){throw 'Isolated startup failed'}
 $started=$true
 & "$pgsql\bin\pg_isready.exe" -h 127.0.0.1 -p 55483 -U isolated_admin -d postgres *> "$work\ready.log"
 if($LASTEXITCODE){throw 'Readiness failed'}
 & "$jdk\java.exe" -cp $cp Probe $work *> "$work\probe.log"
 if($LASTEXITCODE){throw 'Probe failed; see probe.log'}
} finally {
 if($started){
  & "$pgsql\bin\pg_ctl.exe" -D $data -m fast -w -t 60 stop *> "$work\stop.log"
  $stopCode=$LASTEXITCODE
  & "$pgsql\bin\pg_ctl.exe" -D $data status *> "$work\status-after.log"
  $statusCode=$LASTEXITCODE
  & "$pgsql\bin\pg_isready.exe" -h 127.0.0.1 -p 55483 -t 3 *> "$work\ready-after.log"
  "stop=$stopCode status=$statusCode ready=$LASTEXITCODE" | Set-Content "$work\shutdown-result.txt"
 }
 foreach($name in $saved.Keys){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}
}
