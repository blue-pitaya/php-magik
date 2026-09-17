<?php

namespace App\Example;

class Foo
{
    public int $bar;

    public string $x1;

    public string $x2;

    public function print(int $a = 10)
    {
        $this->bar = 30;
        $b = 20;
        $c = $a + $b;

        return $this->bar + $c;
    }

    public function xd()
    {
        return $this->x1.' '.$this->x2;
    }
}
