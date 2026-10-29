OPENQASM 2.0;
include "qelib1.inc";

qreg q[3];
creg a[1];
creg b[1];
creg out[1];

// Teleport the payload |1> prepared on q[2].
x q[2];
h q[0];
cx q[0],q[1];
cx q[2],q[0];
h q[2];
measure q[2] -> a[0];
measure q[0] -> b[0];
if(b==1) x q[1];
if(a==1) z q[1];
measure q[1] -> out[0];
