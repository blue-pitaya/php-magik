<?php

namespace App;

use App\Qux\Foo;

class Main
{
    public function make(): Foo
    {
        return new Foo;
    }

    public function call(Foo $foo)
    {
        return $foo->baz();
    }
}
