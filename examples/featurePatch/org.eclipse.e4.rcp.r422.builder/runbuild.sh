#!/bin/bash
#

ECLIPSE_BASE=/opt/local/e4-self/R422/eclipse

BUILDER=$(pwd)
BASE=$( dirname $BUILDER )
mkdir $BASE/eclipse
LOG=$BASE/log_$( date +%Y%m%d%H%M%S ).txt
exec >>$LOG 2>&1

echo BASE=$BASE
echo BUILDER=$BUILDER

mkdir -p $BASE/features
rm -rf $BASE/features/org.eclipse.e4.rcp.r422.feature
cp -r $BASE/org.eclipse.e4.rcp.r422.feature $BASE/features

$ECLIPSE_BASE/eclipse -noSplash \
-application org.eclipse.ant.core.antRunner \
-buildfile $ECLIPSE_BASE/plugins/org.eclipse.pde.build_3.8.2.v20121114-140810/scripts/build.xml \
-Dbuilder=$BUILDER \
-Dbase=$BASE

