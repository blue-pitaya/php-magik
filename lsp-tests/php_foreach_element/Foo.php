<?php

namespace App;

use App\Models\Bar;

class Foo
{
    /** @var array<Bar> */
    private array $bars = [];

    public function run()
    {
        foreach ($this->bars as $bar) {
            $bar->baz();
        }
        foreach ($this->bars as $key => $value) {
        }
    }
}
