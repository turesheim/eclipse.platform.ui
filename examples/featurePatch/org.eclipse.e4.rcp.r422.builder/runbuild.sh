#!/bin/bash
#

ECLIPSE_BASE=/opt/local/e4-self/R422/eclipse

$ECLIPSE_BASE/eclipse -noSplash \
-application org.eclipse.ant.core.antRunner \
-buildfile $ECLIPSE_BASE/plugins/org.eclipse.pde.build_3.8.2.v20121114-140810/scripts/build.xml \
-Dbuilder=$(pwd) \
-Dbase=$( dirname $(pwd) )

