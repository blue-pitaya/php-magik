<?php

class Counter
{
    public function increment()
    {
        return 1;
    }
}

function run()
{
    $c = new Counter();
    return $c->increment();
}
