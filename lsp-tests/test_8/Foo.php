<?php

namespace App;

use App\NSA\A;
use App\NSB\B;

class Foo
{
    public function call()
    {
        $a = new A;
        $b = new B;

        echo $a->get();
        echo $b->get();
    }
}
