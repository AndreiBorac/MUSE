#!/usr/bin/ruby
# copyright (c) 2012 by andrei borac

require("../053-build-scripts/ruby/import.rb");

require("../057-parts/export.rb");

require("../273-muse/export.rb");

makcmd_automatic_javadoc();

makcmd_script("273-muse-export", [ "273-muse-jar", "057-parts-jar", ],
              makcmd_prelude_bash_javac() +
              [
               "mkdir -p ./export",
               "",
               "echo \"export CLASSPATH='\$CLASSPATH'\" > export/java_classpath",
               "",
               "mkdir ../temp/root",
               "",
               "for i in ./jar/*",
               "do",
               "  if [ -f \"\$i\" ]",
               "  then",
               "    ( cd ../temp/root ; fastjar -x -f ../../root/\"\$i\" )",
               "  fi",
               "done",
               "",
               "( cd ../temp/root ; fastjar -c -M -f ../complete.jar . )",
               "cp ../temp/complete.jar ./jar/",
               "",
               "cp -r ./jar ./export",
               ]);
