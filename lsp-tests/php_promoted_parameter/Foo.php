<?php

namespace App;

use App\Models\Bar;

class Foo
{
    public function __construct(
        private Bar $bar,
    ) {
        $baz = $bar;
        $bar->qux();
    }
}
