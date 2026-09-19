<?php

namespace App\Example;

class Foo
{
    public function print(int $a)
    {
        $b = $a;

        return $b;
    }

    public function bar()
    {
        return 'ok';
    }
}

class Baz
{
    private function ok()
    {
        return 1.2;
    }
}
