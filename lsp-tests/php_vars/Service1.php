<?php

namespace App;

class Service1
{
    public function foo(int $a, int $b)
    {
        $e = new Engine;
        $e->lol = 20;
        $c = $a + $b;
        $d = 10;

        return $c + $d + $e->lol;
    }
}
