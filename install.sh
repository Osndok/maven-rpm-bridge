#!/bin/bash
#
# install.sh
#
# Official build products should have UTC times, and need proper 'version'.
#
set -vexu

VERSION=$(cat .version)
MAJOR=$(echo $VERSION | cut -f1 -d.)

if $(dirname $0)/release.sh --build-needed ; then
	echo "WARNING: not on an official release tag (waiting for interrupt)."
	sleep 2
	VERSION=${MAJOR}-snapshot
fi

#NB: 'install' implies 'package' and 'test'
TZ=UTC mvn -Drelease.version=${VERSION} clean install

# e.g. './mrb-grinder/target/mrb-grinder-1.1.1.jar'
ARTIFACT="$(dirname $0)/mrb-grinder/target/mrb-grinder-${VERSION}.jar"
PACKAGE=mrb-mrb-grinder-v${MAJOR}

if which mrb-grinder ; then
	mrb-grinder "$ARTIFACT"
	yum update -y $PACKAGE
	# We now have grinder version NEW installed, but it was processed with version OLD... can we do better?
	mrb-grinder --remove "$ARTIFACT"
	yum clean all
	mrb-grinder "$ARTIFACT"
	yum update -y $PACKAGE
else
	#TODO: provide a better bootstrapping script... or at least instructions...
	echo 2>&1 "you don't seem to have a current 'mrb-grinder' binary installed, please see '????' for help bootstrapping"
fi

