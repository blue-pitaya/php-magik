<?php

class Point
{
    public int $x;

    public function len()
    {
        return $this->x;
    }
}

function show()
{
    $p = new Point();
    $p->x;
}
