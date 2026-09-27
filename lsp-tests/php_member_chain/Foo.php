<?php

namespace App;

use App\Models\Bar;

class Foo
{
    public function __construct(private ?Bar $bar) {}

    public function run($value)
    {
        $this->bar->baz($value);
        if ($this->bar->qux) {}
    }
}
