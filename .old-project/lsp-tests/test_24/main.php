<?php

class Point
{
    public int $x;

    public function reset()
    {
        $this->x = 0;
    }
}

function show(Point $p)
{
    return $p->x;
}
