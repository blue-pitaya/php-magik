<?php

namespace App;

use App\Models\Bar;

class Foo
{
    private Bar $bar;

    public function run()
    {
        $a = $this->bar->total;
        $c = $this->bar->make();
        $d = $c->total;

        return $a + $d;
    }
}
