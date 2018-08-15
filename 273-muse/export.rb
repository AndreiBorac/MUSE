#!/bin/false
# copyright (c) 2011-2013 by andrei borac

makcmd_import("273-muse-src-java",
              {
                "../273-muse/export" => /^java\/.*\.java$/,
              });

makcmd_import("273-muse-src-c",
              {
                "../273-muse/export" => /^c\/.*\.[hc]$/,
              });

$makcmd_java_all_sources << "273-muse-src-java";

makcmd_script("273-muse-jar", [ "273-muse-src-java", "057-parts-jar", ],
              makcmd_prelude_bash_javac() +
              [
               "javac_jar_packages zs42-muse zs42.muse zs42.muse.view",
              ]);

$makcmd_java_classpath << "jar/zs42-muse.jar";
