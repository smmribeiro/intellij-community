from m1 import <error descr="Cannot find reference 'foo' in 'm1.pyi'">foo</error>
from m1 import <error descr="Cannot find reference 'bar' in 'm1.pyi'">bar</error>
from m1 import <error descr="Cannot find reference 'bar_imported' in 'm1.pyi'">bar_imported</error>
from m1 import baz
from m1 import quux
from m1 import <error descr="Cannot find reference 'm2' in 'm1.pyi'">m2</error>
from m1 import <error descr="Cannot find reference 'm2_imported' in 'm1.pyi'">m2_imported</error>
from m1 import m3

print(foo, bar, bar_imported, baz, m2, m2_imported, m3, quux)
